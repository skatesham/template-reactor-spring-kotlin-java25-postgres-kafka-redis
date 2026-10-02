package com.kotlin.template.audit.application.usecase.retention

import com.kotlin.template.audit.application.port.CustomerAudit
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PurgeCustomerAudit(private val records: CustomerAudit) {
    @Transactional
    fun execute() {
        records.purge()
    }
}
