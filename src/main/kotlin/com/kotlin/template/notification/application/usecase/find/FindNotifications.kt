package com.kotlin.template.notification.application.usecase.find

import com.kotlin.template.notification.application.port.NotificationInbox
import java.util.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FindNotifications(private val inbox: NotificationInbox) {
    @Transactional(readOnly = true)
    fun execute(ownerId: UUID) = inbox.find(ownerId)
}
