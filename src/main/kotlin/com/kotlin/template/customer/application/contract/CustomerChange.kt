package com.kotlin.template.customer.application.contract

import com.kotlin.template.customer.domain.event.CustomerChanged
import java.time.Instant
import java.util.*

/** Public integration contract. Contains no name, email or HTTP model. */
data class CustomerChange(
    val eventId: UUID,
    val customerId: UUID,
    val ownerId: UUID,
    val revision: Long,
    val type: String,
    val occurredAt: Instant,
    val schemaVersion: Int = 1,
) {
    fun validate(now: Instant = Instant.now()) {
        require(schemaVersion == 1 && revision > 0 && eventId.version() == 7 && customerId.version() == 7)
        require(type in setOf("customer.created.v1", "customer.updated.v1", "customer.deleted.v1"))
        require((revision == 1L) == (type == "customer.created.v1"))
        require(!occurredAt.isAfter(now.plusSeconds(60)) && !occurredAt.isBefore(now.minusSeconds(30 * 86400L)))
    }

    companion object {
        fun from(event: CustomerChanged) = CustomerChange(
            event.eventId, event.customerId.value,
            event.ownerId, event.revision, "customer.${event.kind.name.lowercase()}.v1", event.occurredAt
        )
    }
}
