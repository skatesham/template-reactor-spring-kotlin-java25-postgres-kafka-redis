package com.kotlin.template.customer.application.usecase.delivery

import com.kotlin.template.customer.application.port.CustomerEventPublisher
import com.kotlin.template.customer.application.port.CustomerOutbox
import io.micrometer.core.instrument.MeterRegistry
import java.time.Clock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

@Service
class PublishCustomerOutbox(
    private val outbox: CustomerOutbox, private val publisher: CustomerEventPublisher,
    private val clock: Clock, private val meters: MeterRegistry,
    @Value("\${app.customer.outbox.max-attempts:10}") private val maxAttempts: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        require(maxAttempts > 0)
    }

    @Transactional
    fun execute(): Mono<Boolean> = Mono.defer {
        outbox.lockNext().flatMap { pending ->
            val sample = io.micrometer.core.instrument.Timer.start(meters)
            // Only broker/validation failures trigger delivery backoff. A database failure rolls back.
            Mono.defer { pending.change.validate(clock.instant()); publisher.publish(pending.change) }
                .thenReturn(true).onErrorResume { exception ->
                    val attempts = pending.attempts + 1
                    val exhausted = attempts >= maxAttempts
                    val delay = minOf(300L, 1L shl minOf(attempts, 9))
                    outbox.failed(pending.change.eventId, attempts, clock.instant().plusSeconds(delay), exhausted)
                        .doOnSuccess {
                            meters.counter("customer.outbox.failures", "exhausted", exhausted.toString()).increment()
                            log.warn("Customer outbox publication failed eventId={} exhausted={} error={}",
                                pending.change.eventId, exhausted, exception.javaClass.simpleName)
                        }.thenReturn(false)
                }.flatMap { sent ->
                    if (sent) outbox.published(pending.change.eventId)
                        .doOnSuccess { meters.counter("customer.outbox.published").increment() }.thenReturn(true)
                    else Mono.just(true)
                }.doFinally { sample.stop(meters.timer("customer.outbox.publish.duration")) }
        }.defaultIfEmpty(false)
    }
}
