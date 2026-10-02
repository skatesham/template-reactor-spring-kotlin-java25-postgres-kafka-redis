package com.kotlin.template.customer.application.port

import com.kotlin.template.customer.domain.model.Customer
import com.kotlin.template.customer.domain.model.CustomerId
import java.time.Instant
import java.util.UUID
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface CustomerRepository {

    fun list(ownerId: UUID, after: UUID?, limit: Int): Flux<Customer>

    fun create(customer: Customer): Mono<Void>

    fun find(id: CustomerId, ownerId: UUID): Mono<Customer>

    fun findForUpdate(id: CustomerId, ownerId: UUID): Mono<Customer>
    /** Shared row lock held until the reactive query transaction commits. */

    fun lockRevision(id: CustomerId, ownerId: UUID): Mono<Long>

    fun save(customer: Customer): Mono<Void>

    fun delete(customer: Customer): Mono<Void>

    fun expired(before: Instant, limit: Int): Flux<Pair<CustomerId, UUID>>
}
