package com.kotlin.template.notification.infrastructure.persistence.adapter

import com.kotlin.template.customer.application.contract.CustomerChange
import com.kotlin.template.notification.application.port.CustomerNotifications
import java.sql.Timestamp
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class JdbcCustomerNotifications(private val jdbc: JdbcTemplate) : CustomerNotifications {
    override fun record(change: CustomerChange): Boolean {
        jdbc.update(
            """INSERT INTO customer_notifications_cursor(customer_id, revision, updated_at)
            VALUES (?, 0, CURRENT_TIMESTAMP) ON CONFLICT DO NOTHING""", change.customerId
        )
        val cursor =
            jdbc.query(
                "SELECT revision, deleted_at FROM customer_notifications_cursor WHERE customer_id=? FOR UPDATE",
                { rs, _ -> rs.getLong("revision") to rs.getTimestamp("deleted_at") }, change.customerId
            ).single()
        val revision = cursor.first
        // High watermark prevents duplicates even after individual receipt retention expires.
        if (change.revision <= revision) return false
        check(cursor.second == null) { "Customer was already deleted" }
        check(change.revision == revision + 1) { "Customer event sequence gap" }
        jdbc.update(
            """INSERT INTO customer_notifications(event_id, customer_id, owner_id, revision, event_type, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?)""", change.eventId, change.customerId, change.ownerId, change.revision,
            change.type, Timestamp.from(change.occurredAt)
        )
        jdbc.update(
            "UPDATE customer_notifications_cursor SET revision=?, updated_at=CURRENT_TIMESTAMP, deleted_at=CASE WHEN ?='customer.deleted.v1' THEN CURRENT_TIMESTAMP ELSE NULL END WHERE customer_id=?",
            change.revision, change.type, change.customerId
        )
        return true
    }

    override fun purge() {
        jdbc.update("DELETE FROM customer_notifications WHERE recorded_at < CURRENT_TIMESTAMP - INTERVAL '30 days'")
        jdbc.update("DELETE FROM customer_notifications_cursor WHERE deleted_at < CURRENT_TIMESTAMP - INTERVAL '31 days'")
    }
}
