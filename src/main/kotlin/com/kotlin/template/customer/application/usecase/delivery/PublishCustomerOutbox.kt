package com.kotlin.template.customer.application.usecase.delivery

import com.kotlin.template.customer.application.port.CustomerEventPublisher
import com.kotlin.template.customer.application.port.CustomerOutbox
import io.micrometer.core.instrument.MeterRegistry
import java.time.Clock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
    fun execute(): Boolean {
        val pending = outbox.lockNext() ?: return false
        val sample = io.micrometer.core.instrument.Timer.start(meters)
        try {
            pending.change.validate(clock.instant())
            publisher.publish(pending.change)
            outbox.published(pending.change.eventId)
            meters.counter("customer.outbox.published").increment()
        } catch (exception: Exception) {
            if (exception is InterruptedException) Thread.currentThread().interrupt()
            val attempts = pending.attempts + 1
            val exhausted = attempts >= maxAttempts
            val delay = minOf(300L, 1L shl minOf(attempts, 9))
            outbox.failed(pending.change.eventId, attempts, clock.instant().plusSeconds(delay), exhausted)
            meters.counter("customer.outbox.failures", "exhausted", exhausted.toString()).increment()
            // Exception messages and payloads can contain personal data. Only log the class.
            log.warn(
                "Customer outbox publication failed eventId={} exhausted={} error={}",
                pending.change.eventId, exhausted, exception.javaClass.simpleName
            )
        } finally {
            sample.stop(meters.timer("customer.outbox.publish.duration"))
        }
        return true
    }
}
