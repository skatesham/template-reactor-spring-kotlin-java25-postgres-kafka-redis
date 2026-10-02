package com.kotlin.template.customer.application.usecase.delivery

import com.kotlin.template.customer.application.exception.CustomerNotFound
import com.kotlin.template.customer.application.port.CustomerEventPublisher
import com.kotlin.template.customer.application.port.CustomerOutbox
import io.micrometer.core.instrument.MeterRegistry
import java.util.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

@Service
class RecoverCustomerDelivery(
    private val outbox: CustomerOutbox, private val publisher: CustomerEventPublisher,
    private val meters: MeterRegistry
) {

    @Transactional
    fun retry(eventId: UUID): Mono<Void> = outbox.retryFailed(eventId).flatMap { retried ->
        if (!retried) Mono.error(CustomerNotFound()) else Mono.fromRunnable<Void> {
            meters.counter("customer.outbox.recoveries", "action", "retry").increment()
        }
    }

    @Transactional(readOnly = true)
    fun replay(eventId: UUID): Mono<Void> = outbox.findPublished(eventId).switchIfEmpty(Mono.error(CustomerNotFound()))
        .flatMap { change -> change.validate(); publisher.publish(change) }
        .doOnSuccess { meters.counter("customer.outbox.recoveries", "action", "replay").increment() }
}
