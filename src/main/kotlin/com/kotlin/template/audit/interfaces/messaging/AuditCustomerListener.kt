package com.kotlin.template.audit.interfaces.messaging

import com.kotlin.template.audit.application.usecase.record.RecordCustomerAudit
import com.kotlin.template.customer.application.contract.CustomerChange
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
@ConditionalOnProperty(name = ["app.customer.messaging.enabled"], havingValue = "true", matchIfMissing = true)
class AuditCustomerListener(private val mapper: ObjectMapper, private val record: RecordCustomerAudit) {
    @KafkaListener(
        id = "customer-audit-v1", groupId = "customer-audit-v1", topics = ["customer.changes.v1"],
        containerFactory = "auditKafkaFactory", autoStartup = "\${app.customer.consumers.enabled:true}"
    )
    fun consume(message: ConsumerRecord<String, String>) {
        try {
            val change = mapper.readValue(message.value(), CustomerChange::class.java)
            require(message.key() == change.customerId.toString()) { "Invalid customer event key" }
            record.execute(change)
        } catch (_: Exception) {
            // Do not allow deserialization errors to echo a rejected payload into logs or DLT.
            throw AuditCustomerDeliveryFailure()
        }
    }
}
