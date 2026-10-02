package com.kotlin.template.customer.application.port

import java.util.*

/** A PostgreSQL reservation, held in the same transaction as Customer and Outbox. */
interface CustomerCreationRequests {
    fun reserve(ownerId: UUID, key: UUID, name: String, email: String): UUID?
    fun complete(ownerId: UUID, key: UUID, customerId: UUID)
    fun purge()
}
