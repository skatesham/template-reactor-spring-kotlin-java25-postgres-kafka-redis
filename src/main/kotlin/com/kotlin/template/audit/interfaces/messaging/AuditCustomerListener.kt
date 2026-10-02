package com.kotlin.template.audit.interfaces.messaging

import com.kotlin.template.audit.application.usecase.record.RecordCustomerAudit
import com.kotlin.template.customer.application.contract.CustomerChange
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
class AuditCustomerListener(private val mapper: ObjectMapper, private val record: RecordCustomerAudit,
    private val factory: KafkaListenerFactory, private val kafka: KafkaTemplate<String, String>, private val meters: MeterRegistry,
    @Value("\${app.customer.consumer.backoff-ms:1000}") private val backoff: Long) {
    @KafkaListener(id = "customer-audit-v1", groupId = "customer-audit-v1", topics = ["customer.changes.v1"],
        containerFactory = "auditKafkaFactory", autoStartup = "\${app.customer.consumers.enabled:true}")

    fun consume(message: ConsumerRecord<String, String>): Mono<Void> = factory.deliver(message, "audit",
        "customer.changes.v1.audit.DLT", kafka, meters, mapper, backoff) {
        Mono.defer {
            val change = mapper.readValue(message.value(), CustomerChange::class.java)
            require(message.key() == change.customerId.toString()) { "Invalid customer event key" }
            record.execute(change)
        }.onErrorMap { AuditCustomerDeliveryFailure() }
    }
}
