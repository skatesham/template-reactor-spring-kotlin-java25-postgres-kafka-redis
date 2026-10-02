package com.kotlin.template.customer.domain.repository

import com.kotlin.template.customer.domain.model.Customer
import com.kotlin.template.customer.domain.model.CustomerId
import java.time.Instant
import java.util.*

interface CustomerRepository {
    fun list(ownerId: UUID, after: UUID?, limit: Int): List<Customer>
    fun create(customer: Customer)
    fun find(id: CustomerId, ownerId: UUID): Customer?
    fun findForUpdate(id: CustomerId, ownerId: UUID): Customer?

    /** Holds a shared row lock until query commit, protecting cache fill against deletion. */
    fun lockRevision(id: CustomerId, ownerId: UUID): Long?
    fun save(customer: Customer)
    fun delete(customer: Customer)
    fun expired(before: Instant, limit: Int): List<Pair<CustomerId, UUID>>
}
