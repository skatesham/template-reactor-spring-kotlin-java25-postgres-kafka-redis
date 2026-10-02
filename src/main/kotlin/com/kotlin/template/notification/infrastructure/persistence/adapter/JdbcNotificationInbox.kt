package com.kotlin.template.notification.infrastructure.persistence.adapter

import com.kotlin.template.notification.application.port.NotificationInbox
import com.kotlin.template.notification.application.result.NotificationDetails
import java.util.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class JdbcNotificationInbox(private val jdbc: JdbcTemplate) : NotificationInbox {
    override fun find(ownerId: UUID): List<NotificationDetails> = jdbc.query(
        """
        SELECT * FROM customer_notifications WHERE owner_id=? ORDER BY recorded_at DESC, revision DESC LIMIT 100
    """, { rs, _ ->
        NotificationDetails(
            rs.getObject("event_id", UUID::class.java), rs.getObject("customer_id", UUID::class.java),
            rs.getLong("revision"), rs.getString("event_type"), rs.getTimestamp("occurred_at").toInstant()
        )
    }, ownerId
    )
}
