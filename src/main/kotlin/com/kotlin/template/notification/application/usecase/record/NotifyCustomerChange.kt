package com.kotlin.template.notification.application.usecase.record

import com.kotlin.template.customer.application.contract.CustomerChange
import com.kotlin.template.notification.application.port.CustomerNotifications
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

@Service
class NotifyCustomerChange(private val records: CustomerNotifications, private val meters: MeterRegistry) {

    @Transactional
    fun execute(change: CustomerChange): Mono<Void> = Mono.defer {
        change.validate()
        records.record(change).doOnNext { created ->
            meters.counter("customer.consumer.records", "consumer", "notification", "result", if (created) "created" else "duplicate").increment()
        }.then()
    }
}
