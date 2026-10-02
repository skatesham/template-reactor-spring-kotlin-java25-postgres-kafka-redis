package com.kotlin.template.customer.infrastructure.messaging

import com.kotlin.template.customer.application.contract.CustomerChange
import com.kotlin.template.customer.application.port.CustomerEventPublisher
import java.util.concurrent.TimeUnit
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class KafkaCustomerEventPublisher(private val kafka: KafkaTemplate<String, String>, private val mapper: ObjectMapper) :
    CustomerEventPublisher {
    override fun publish(change: CustomerChange) {
        kafka.send("customer.changes.v1", change.customerId.toString(), mapper.writeValueAsString(change))
            .get(10, TimeUnit.SECONDS)
    }
}
