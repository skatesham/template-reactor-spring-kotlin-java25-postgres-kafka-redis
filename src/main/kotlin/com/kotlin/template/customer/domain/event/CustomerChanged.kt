package com.kotlin.template.customer.domain.event

import com.kotlin.template.customer.domain.model.CustomerId
import java.time.Instant
import java.util.*

data class CustomerChanged(
    val eventId: UUID, val customerId: CustomerId, val ownerId: UUID,
    val revision: Long, val kind: CustomerChangeKind, val occurredAt: Instant,
)
