package com.kotlin.template.customer.application.usecase.retention

import com.kotlin.template.customer.application.port.CustomerCreationRequests
import com.kotlin.template.customer.application.port.CustomerOutbox
import java.time.Clock
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PurgeCustomerOutbox(
    private val outbox: CustomerOutbox,
    private val clock: Clock,
    private val requests: CustomerCreationRequests
) {
    @Transactional
    fun execute() {
        outbox.purge(clock.instant().minusSeconds(30 * 86400L)); requests.purge()
    }
}
