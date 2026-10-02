package com.kotlin.template.notification.application.port

import com.kotlin.template.notification.application.result.NotificationDetails
import java.util.*
import reactor.core.publisher.Flux

fun interface NotificationInbox {

    fun find(ownerId: UUID): Flux<NotificationDetails>
}
