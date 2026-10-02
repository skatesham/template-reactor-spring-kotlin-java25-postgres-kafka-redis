package com.kotlin.template.audit.application.usecase.retention

import com.kotlin.template.audit.application.port.CustomerAudit
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

@Service
class PurgeCustomerAudit(private val records: CustomerAudit) {

    @Transactional
    fun execute(): Mono<Void> = Mono.defer { records.purge() }
}
