package com.kotlin.template.customer.infrastructure.messaging

import com.kotlin.template.customer.application.contract.CustomerChange
import com.kotlin.template.customer.application.exception.CustomerDeliveryUnavailable
import com.kotlin.template.customer.application.port.CustomerEventPublisher
import java.time.Duration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import tools.jackson.databind.ObjectMapper

@Component
class KafkaCustomerEventPublisher(private val kafka: KafkaTemplate<String, String>, private val mapper: ObjectMapper) : CustomerEventPublisher {

    override fun publish(change: CustomerChange): Mono<Void> = Mono.defer {
        // Kafka send can wait for metadata or buffer allocation before returning its future.
        Mono.fromFuture(kafka.send("customer.changes.v1", change.customerId.toString(), mapper.writeValueAsString(change)), true)
    }.subscribeOn(Schedulers.boundedElastic()).timeout(Duration.ofSeconds(10)).onErrorMap { CustomerDeliveryUnavailable() }.then()
}
