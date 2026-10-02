package com.kotlin.template.customer.application.port

import com.kotlin.template.customer.application.contract.CustomerChange
import java.time.Instant
import java.util.*
import reactor.core.publisher.Mono

interface CustomerOutbox {

    fun findPublished(id: UUID): Mono<CustomerChange>

    fun retryFailed(id: UUID): Mono<Boolean>

    fun append(change: CustomerChange): Mono<Void>

    fun lockNext(): Mono<PendingCustomerChange>

    fun published(id: UUID): Mono<Void>

    fun failed(id: UUID, attempts: Int, nextAttempt: Instant, exhausted: Boolean): Mono<Void>

    fun purge(before: Instant): Mono<Void>
}
