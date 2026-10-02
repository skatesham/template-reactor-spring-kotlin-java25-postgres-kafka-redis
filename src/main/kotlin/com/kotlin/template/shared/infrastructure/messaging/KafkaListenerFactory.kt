package com.kotlin.template.shared.infrastructure.messaging

import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.TimeUnit
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.RetryListener
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class KafkaListenerFactory {
    fun create(
        consumer: String, deadLetterTopic: String, metricPrefix: String,
        consumerFactory: ConsumerFactory<String, String>, kafka: KafkaTemplate<String, String>,
        meters: MeterRegistry, mapper: ObjectMapper, initialBackoff: Long
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        require(initialBackoff > 0)
        val backoff = ExponentialBackOffWithMaxRetries(3).apply {
            initialInterval = initialBackoff; multiplier = 2.0; maxInterval = maxOf(initialBackoff, 10000L)
        }
        // Use a minimal DLT envelope: never copy an invalid payload, exception message or stack trace.
        // A valid message is recoverable by eventId from the retained outbox.
        val errors = DefaultErrorHandler({ record, exception ->
            val headers = org.apache.kafka.common.header.internals.RecordHeaders()
            headers.add(RecordHeader("original-topic", record.topic().toByteArray()))
            headers.add(RecordHeader("original-partition", record.partition().toString().toByteArray()))
            headers.add(RecordHeader("original-offset", record.offset().toString().toByteArray()))
            headers.add(RecordHeader("failure-type", exception.javaClass.simpleName.toByteArray()))
            val eventId = runCatching {
                java.util.UUID.fromString(mapper.readTree(record.value().toString())["eventId"].asString()).toString()
            }.getOrNull()
            eventId?.let { headers.add(RecordHeader("event-id", it.toByteArray())) }
            // Only UUID keys are retained. Payload itself remains in the original topic for 7 days.
            val key =
                record.key()?.toString()?.let { runCatching { java.util.UUID.fromString(it).toString() }.getOrNull() }
            val dlt = org.apache.kafka.clients.producer.ProducerRecord<String, String>(
                deadLetterTopic, record.partition(), key, "{\"status\":\"failed\"}", headers
            )
            kafka.send(dlt).get(10, TimeUnit.SECONDS) // throw on send failure: original offset must not advance
            meters.counter("$metricPrefix.dlt", "consumer", consumer).increment()
        }, backoff)
        errors.setRetryListeners(object : RetryListener {
            override fun failedDelivery(record: ConsumerRecord<*, *>, ex: Exception?, deliveryAttempt: Int) {
                meters.counter("$metricPrefix.failures", "consumer", consumer).increment()
            }
        })
        return ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            setConsumerFactory(consumerFactory)
            setCommonErrorHandler(errors)
            setConcurrency(3)
            containerProperties.ackMode = ContainerProperties.AckMode.RECORD
            // Avoid the default listener error logger copying payloads/exception messages into logs.
            containerProperties.isObservationEnabled = true
            containerProperties.isLogContainerConfig = false
        }
    }
}
