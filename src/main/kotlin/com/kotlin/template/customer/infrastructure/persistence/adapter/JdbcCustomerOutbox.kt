package com.kotlin.template.customer.infrastructure.persistence.adapter

import com.kotlin.template.customer.application.contract.CustomerChange
import com.kotlin.template.customer.application.port.CustomerOutbox
import com.kotlin.template.customer.application.port.PendingCustomerChange
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class JdbcCustomerOutbox(private val jdbc: JdbcTemplate) : CustomerOutbox {
    override fun findPublished(id: UUID): CustomerChange? = jdbc.query(
        "SELECT * FROM customer_outbox WHERE event_id=? AND status='PUBLISHED'",
        { rs, _ ->
            CustomerChange(
                rs.getObject("event_id", UUID::class.java), rs.getObject("customer_id", UUID::class.java),
                rs.getObject("owner_id", UUID::class.java), rs.getLong("revision"), rs.getString("event_type"),
                rs.getTimestamp("occurred_at").toInstant()
            )
        }, id
    ).firstOrNull()

    override fun retryFailed(id: UUID): Boolean = jdbc.update(
        "UPDATE customer_outbox SET status='PENDING', attempts=0, next_attempt_at=CURRENT_TIMESTAMP WHERE event_id=? AND status='FAILED'",
        id
    ) == 1

    override fun append(change: CustomerChange) {
        jdbc.update(
            """INSERT INTO customer_outbox(event_id, customer_id, owner_id, revision, event_type, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?)""", change.eventId, change.customerId, change.ownerId,
            change.revision, change.type, Timestamp.from(change.occurredAt)
        )
    }

    override fun lockNext(): PendingCustomerChange? = jdbc.query(
        """
        SELECT o.* FROM customer_outbox o
        WHERE o.status = 'PENDING' AND o.next_attempt_at <= CURRENT_TIMESTAMP
          AND NOT EXISTS (SELECT 1 FROM customer_outbox previous
            WHERE previous.customer_id = o.customer_id AND previous.revision < o.revision
              AND previous.status <> 'PUBLISHED')
        ORDER BY o.occurred_at, o.event_id LIMIT 1 FOR UPDATE OF o SKIP LOCKED
    """, { rs, _ ->
            PendingCustomerChange(
                CustomerChange(
                    rs.getObject("event_id", UUID::class.java),
                    rs.getObject("customer_id", UUID::class.java), rs.getObject("owner_id", UUID::class.java),
                    rs.getLong("revision"), rs.getString("event_type"), rs.getTimestamp("occurred_at").toInstant()
                ), rs.getInt("attempts")
            )
        }).firstOrNull()

    override fun published(id: UUID) {
        jdbc.update(
            "UPDATE customer_outbox SET status='PUBLISHED', published_at=CURRENT_TIMESTAMP WHERE event_id=?",
            id
        )
    }

    override fun failed(id: UUID, attempts: Int, nextAttempt: Instant, exhausted: Boolean) {
        jdbc.update(
            "UPDATE customer_outbox SET attempts=?, next_attempt_at=?, status=? WHERE event_id=?",
            attempts, Timestamp.from(nextAttempt), if (exhausted) "FAILED" else "PENDING", id
        )
    }

    override fun purge(before: Instant) {
        jdbc.update("DELETE FROM customer_outbox WHERE status='PUBLISHED' AND published_at < ?", Timestamp.from(before))
    }
}
