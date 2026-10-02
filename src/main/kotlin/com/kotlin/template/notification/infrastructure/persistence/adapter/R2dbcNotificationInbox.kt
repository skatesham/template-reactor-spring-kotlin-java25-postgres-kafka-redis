package com.kotlin.template.notification.infrastructure.persistence.adapter

import com.kotlin.template.notification.application.port.NotificationInbox
import com.kotlin.template.notification.application.result.NotificationDetails
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcNotificationInbox(private val db: DatabaseClient) : NotificationInbox {

    override fun find(ownerId: UUID) = db.sql("SELECT * FROM customer_notifications WHERE owner_id=:owner ORDER BY recorded_at DESC, revision DESC LIMIT 100")
        .bind("owner", ownerId).map { row, _ -> NotificationDetails(row.get("event_id", UUID::class.java)!!,
            row.get("customer_id", UUID::class.java)!!, row.get("revision", Long::class.javaObjectType)!!.toLong(),
            row.get("event_type", String::class.java)!!, row.get("occurred_at", OffsetDateTime::class.java)!!.toInstant()) }.all()
}
