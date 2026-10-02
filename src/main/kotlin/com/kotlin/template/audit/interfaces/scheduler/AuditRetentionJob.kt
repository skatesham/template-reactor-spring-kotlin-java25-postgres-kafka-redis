package com.kotlin.template.audit.interfaces.scheduler

import com.kotlin.template.audit.application.usecase.retention.PurgeCustomerAudit
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
@ConditionalOnProperty(name = ["app.customer.jobs.enabled"], havingValue = "true", matchIfMissing = true)
class AuditRetentionJob(private val purge: PurgeCustomerAudit) {

    @Scheduled(
        fixedDelayString = "\${app.customer.retention.poll-ms:3600000}",
        initialDelayString = "\${app.customer.retention.poll-ms:3600000}"
    )
    fun retention(): Mono<Void> = Mono.defer { purge.execute() }
}
