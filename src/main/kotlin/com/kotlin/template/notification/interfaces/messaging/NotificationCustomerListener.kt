package com.kotlin.template.notification.interfaces.messaging

import com.kotlin.template.customer.application.contract.CustomerChange
import com.kotlin.template.customer.application.usecase.verify.VerifyCustomerChange
import com.kotlin.template.notification.application.usecase.record.NotifyCustomerChange
import com.kotlin.template.shared.infrastructure.messaging.KafkaListenerFactory
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

@Component
@ConditionalOnProperty(name = ["app.customer.messaging.enabled"], havingValue = "true", matchIfMissing = true)
class NotificationCustomerListener(private val mapper: ObjectMapper, private val record: NotifyCustomerChange,
    private val verify: VerifyCustomerChange,
    private val factory: KafkaListenerFactory, private val kafka: KafkaTemplate<String, String>, private val meters: MeterRegistry,
    @Value("\${app.customer.consumer.backoff-ms:1000}") private val backoff: Long) {
    @KafkaListener(id = "customer-notification-v1", groupId = "customer-notification-v1", topics = ["customer.changes.v1"],
        containerFactory = "notificationKafkaFactory", autoStartup = "\${app.customer.consumers.enabled:true}")

    fun consume(message: ConsumerRecord<String, String>): Mono<Void> = factory.deliver(message, "notification",
        "customer.changes.v1.notification.DLT", kafka, meters, mapper, backoff) {
        Mono.defer {
            val change = mapper.readValue(message.value(), CustomerChange::class.java)
            require(message.key() == change.customerId.toString()) { "Invalid customer event key" }
            // Verify before opening the notification database transaction.
            verify.execute(change).then(Mono.defer { record.execute(change) })
        }.onErrorMap { NotificationCustomerDeliveryFailure() }
    }
}
