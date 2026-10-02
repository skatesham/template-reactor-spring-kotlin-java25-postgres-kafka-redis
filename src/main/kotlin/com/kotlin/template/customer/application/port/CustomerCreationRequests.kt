package com.kotlin.template.customer.application.port

import java.util.*
import reactor.core.publisher.Mono

/** A PostgreSQL reservation, held in the same transaction as Customer and Outbox. */
interface CustomerCreationRequests {

    fun reserve(ownerId: UUID, key: UUID, name: String, email: String): Mono<UUID>

    fun complete(ownerId: UUID, key: UUID, customerId: UUID): Mono<Void>

    fun purge(): Mono<Void>
}
