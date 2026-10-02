package com.kotlin.template.customer.application.port

import com.kotlin.template.customer.application.contract.CustomerChange
import java.time.Instant
import java.util.*

interface CustomerOutbox {
    fun findPublished(id: UUID): CustomerChange?
    fun retryFailed(id: UUID): Boolean
    fun append(change: CustomerChange)
    fun lockNext(): PendingCustomerChange?
    fun published(id: UUID)
    fun failed(id: UUID, attempts: Int, nextAttempt: Instant, exhausted: Boolean)
    fun purge(before: Instant)
}
