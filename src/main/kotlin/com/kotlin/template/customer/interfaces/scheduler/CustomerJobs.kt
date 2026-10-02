package com.kotlin.template.customer.interfaces.scheduler

import com.kotlin.template.customer.application.usecase.delivery.PublishCustomerOutbox
import com.kotlin.template.customer.application.usecase.retention.DeleteExpiredCustomers
import com.kotlin.template.customer.application.usecase.retention.PurgeCustomerOutbox
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["app.customer.jobs.enabled"], havingValue = "true", matchIfMissing = true)
class CustomerJobs(
    private val publish: PublishCustomerOutbox, private val purge: PurgeCustomerOutbox,
    private val expired: DeleteExpiredCustomers, private val meters: MeterRegistry
) {
    @Scheduled(fixedDelayString = "\${app.customer.outbox.poll-ms:500}")
    fun publish() {
        repeat(20) { if (!publish.execute()) return }
    }

    @Scheduled(
        fixedDelayString = "\${app.customer.retention.poll-ms:3600000}",
        initialDelayString = "\${app.customer.retention.poll-ms:3600000}"
    )
    fun retention() {
        expired.execute()
        purge.execute()
        meters.counter("customer.retention.runs").increment()
    }
}
