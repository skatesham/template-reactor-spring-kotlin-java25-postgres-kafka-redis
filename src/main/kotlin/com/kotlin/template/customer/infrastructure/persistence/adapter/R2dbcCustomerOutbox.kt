package com.kotlin.template.customer.infrastructure.persistence.adapter

import com.kotlin.template.customer.application.contract.CustomerChange
import com.kotlin.template.customer.application.port.CustomerOutbox
import com.kotlin.template.customer.application.port.PendingCustomerChange
import io.r2dbc.spi.Row
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

@Repository
class R2dbcCustomerOutbox(private val db: DatabaseClient) : CustomerOutbox {

    override fun findPublished(id: UUID) = db.sql("SELECT * FROM customer_outbox WHERE event_id=:id AND status='PUBLISHED'")
        .bind("id", id).map { row, _ -> change(row) }.one()

    override fun retryFailed(id: UUID) = db.sql("UPDATE customer_outbox SET status='PENDING', attempts=0, next_attempt_at=CURRENT_TIMESTAMP WHERE event_id=:id AND status='FAILED'")
        .bind("id", id).fetch().rowsUpdated().map { it == 1L }

    override fun append(change: CustomerChange) = db.sql("""INSERT INTO customer_outbox(event_id, customer_id, owner_id, revision, event_type, occurred_at)
        VALUES (:event, :customer, :owner, :revision, :type, :occurred)""")
        .bind("event", change.eventId).bind("customer", change.customerId).bind("owner", change.ownerId)
        .bind("revision", change.revision).bind("type", change.type).bind("occurred", change.occurredAt.atOffset(ZoneOffset.UTC))
        .fetch().rowsUpdated().then()

    override fun lockNext() = db.sql("""SELECT o.* FROM customer_outbox o
        WHERE o.status='PENDING' AND o.next_attempt_at <= CURRENT_TIMESTAMP
        AND NOT EXISTS (SELECT 1 FROM customer_outbox previous WHERE previous.customer_id=o.customer_id
            AND previous.revision < o.revision AND previous.status <> 'PUBLISHED')
        ORDER BY o.occurred_at, o.event_id LIMIT 1 FOR UPDATE OF o SKIP LOCKED""")
        .map { row, _ -> PendingCustomerChange(change(row), row.get("attempts", Int::class.javaObjectType)!!.toInt()) }.one()

    override fun published(id: UUID) = db.sql("UPDATE customer_outbox SET status='PUBLISHED', published_at=CURRENT_TIMESTAMP WHERE event_id=:id")
        .bind("id", id).fetch().rowsUpdated().then()

    override fun failed(id: UUID, attempts: Int, nextAttempt: Instant, exhausted: Boolean) = db.sql(
        "UPDATE customer_outbox SET attempts=:attempts, next_attempt_at=:next, status=:status WHERE event_id=:id"
    ).bind("attempts", attempts).bind("next", nextAttempt.atOffset(ZoneOffset.UTC))
        .bind("status", if (exhausted) "FAILED" else "PENDING").bind("id", id).fetch().rowsUpdated().then()

    override fun purge(before: Instant) = db.sql("DELETE FROM customer_outbox WHERE status='PUBLISHED' AND published_at < :before")
        .bind("before", before.atOffset(ZoneOffset.UTC)).fetch().rowsUpdated().then()

    private fun change(row: Row) = CustomerChange(row.get("event_id", UUID::class.java)!!,
        row.get("customer_id", UUID::class.java)!!, row.get("owner_id", UUID::class.java)!!,
        row.get("revision", Long::class.javaObjectType)!!.toLong(), row.get("event_type", String::class.java)!!,
        row.get("occurred_at", OffsetDateTime::class.java)!!.toInstant())
}
