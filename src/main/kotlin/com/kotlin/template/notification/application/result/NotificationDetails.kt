package com.kotlin.template.notification.application.result

import java.time.Instant
import java.util.*

data class NotificationDetails(
    val eventId: UUID,
    val customerId: UUID,
    val revision: Long,
    val type: String,
    val occurredAt: Instant
)
