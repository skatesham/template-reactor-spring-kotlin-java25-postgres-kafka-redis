package com.kotlin.template.notification.application.port

import com.kotlin.template.notification.application.result.NotificationDetails
import java.util.*

fun interface NotificationInbox {
    fun find(ownerId: UUID): List<NotificationDetails>
}
