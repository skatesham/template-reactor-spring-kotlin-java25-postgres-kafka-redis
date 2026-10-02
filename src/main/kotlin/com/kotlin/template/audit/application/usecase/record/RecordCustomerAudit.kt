package com.kotlin.template.audit.application.usecase.record

import com.kotlin.template.audit.application.port.CustomerAudit
import com.kotlin.template.customer.application.contract.CustomerChange
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

@Service
class RecordCustomerAudit(private val records: CustomerAudit, private val meters: MeterRegistry) {

    @Transactional
    fun execute(change: CustomerChange): Mono<Void> = Mono.defer {
        change.validate()
        records.record(change).doOnNext { created ->
            meters.counter("customer.consumer.records", "consumer", "audit", "result", if (created) "created" else "duplicate").increment()
        }.then()
    }
}
