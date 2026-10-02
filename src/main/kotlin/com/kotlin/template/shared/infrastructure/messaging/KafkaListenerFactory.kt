package com.kotlin.template.shared.infrastructure.messaging

import io.micrometer.core.instrument.MeterRegistry
import java.time.Duration
import java.util.UUID
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.stereotype.Component
import org.springframework.util.backoff.FixedBackOff
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.util.retry.Retry
import tools.jackson.databind.ObjectMapper

@Component
class KafkaListenerFactory {

    fun create(consumerFactory: ConsumerFactory<String, String>, initialBackoff: Long): ConcurrentKafkaListenerContainerFactory<String, String> {
        require(initialBackoff > 0)
        return ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            setConsumerFactory(consumerFactory)
            // DLT failure must retain the original offset, even after repeated broker outages.
            setCommonErrorHandler(DefaultErrorHandler(FixedBackOff(initialBackoff, FixedBackOff.UNLIMITED_ATTEMPTS)).apply {
                setLogLevel(org.springframework.kafka.KafkaException.Level.DEBUG)
            })
            setConcurrency(3)
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
            containerProperties.isAsyncAcks = true
            containerProperties.isObservationEnabled = true
            containerProperties.isLogContainerConfig = false
        }
    }

    fun deliver(record: ConsumerRecord<String, String>, consumer: String, deadLetterTopic: String,
        kafka: KafkaTemplate<String, String>, meters: MeterRegistry, mapper: ObjectMapper,
        initialBackoff: Long, action: () -> Mono<Void>): Mono<Void> = Mono.defer(action)
        .doOnError { meters.counter("customer.consumer.failures", "consumer", consumer).increment() }
        .retryWhen(Retry.backoff(3, Duration.ofMillis(initialBackoff)).maxBackoff(Duration.ofMillis(maxOf(initialBackoff, 10000L))).jitter(0.0))
        .onErrorResume { error -> Mono.defer {
            val headers = RecordHeaders()
            headers.add("original-topic", record.topic().toByteArray())
            headers.add("original-partition", record.partition().toString().toByteArray())
            headers.add("original-offset", record.offset().toString().toByteArray())
            headers.add("failure-type", error.javaClass.simpleName.toByteArray())
            runCatching { UUID.fromString(mapper.readTree(record.value())["eventId"].asString()).toString() }
                .getOrNull()?.let { headers.add("event-id", it.toByteArray()) }
            val key = runCatching { UUID.fromString(record.key()).toString() }.getOrNull()
            val dlt = ProducerRecord<String, String>(deadLetterTopic, record.partition(), key, "{\"status\":\"failed\"}", headers)
            Mono.fromFuture(kafka.send(dlt), true).timeout(Duration.ofSeconds(10))
                .doOnSuccess { meters.counter("customer.consumer.dlt", "consumer", consumer).increment() }.then()
        }.subscribeOn(Schedulers.boundedElastic()) }
}
