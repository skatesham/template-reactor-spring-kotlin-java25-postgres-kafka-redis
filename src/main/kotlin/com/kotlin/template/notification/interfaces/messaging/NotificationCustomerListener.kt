package com.kotlin.template.notification.interfaces.messaging

import com.kotlin.template.customer.application.contract.CustomerChange
import com.kotlin.template.notification.application.usecase.record.NotifyCustomerChange
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
@ConditionalOnProperty(name = ["app.customer.messaging.enabled"], havingValue = "true", matchIfMissing = true)
class NotificationCustomerListener(private val mapper: ObjectMapper, private val record: NotifyCustomerChange) {
    @KafkaListener(
        id = "customer-notification-v1", groupId = "customer-notification-v1", topics = ["customer.changes.v1"],
        containerFactory = "notificationKafkaFactory", autoStartup = "\${app.customer.consumers.enabled:true}"
    )
    fun consume(message: ConsumerRecord<String, String>) {
        try {
            val change = mapper.readValue(message.value(), CustomerChange::class.java)
            require(message.key() == change.customerId.toString()) { "Invalid customer event key" }
            record.execute(change)
        } catch (_: Exception) {
            // Do not allow deserialization errors to echo a rejected payload into logs or DLT.
            throw NotificationCustomerDeliveryFailure()
        }
    }
}
