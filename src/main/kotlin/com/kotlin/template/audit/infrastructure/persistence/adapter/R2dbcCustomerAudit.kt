package com.kotlin.template.audit.infrastructure.persistence.adapter

import com.kotlin.template.audit.application.port.CustomerAudit
import com.kotlin.template.customer.application.contract.CustomerChange
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
class R2dbcCustomerAudit(private val db: DatabaseClient) : CustomerAudit {

    override fun record(change: CustomerChange): Mono<Boolean> = db.sql(
        "INSERT INTO customer_audit_cursor(customer_id, revision, updated_at) VALUES (:id, 0, CURRENT_TIMESTAMP) ON CONFLICT DO NOTHING"
    ).bind("id", change.customerId).fetch().rowsUpdated().then(db.sql(
        "SELECT revision, deleted_at FROM customer_audit_cursor WHERE customer_id=:id FOR UPDATE"
    ).bind("id", change.customerId).map { row, _ ->
        row.get("revision", Long::class.javaObjectType)!!.toLong() to (row.get("deleted_at", OffsetDateTime::class.java) != null)
    }.one()).flatMap { (revision, deleted) ->
        if (change.revision <= revision) Mono.just(false) else {
            check(!deleted) { "Customer was already deleted" }
            check(change.revision == revision + 1) { "Customer event sequence gap" }
            db.sql("""INSERT INTO customer_audit(event_id, customer_id, owner_id, revision, event_type, occurred_at)
                VALUES (:event, :customer, :owner, :revision, :type, :occurred)""")
                .bind("event", change.eventId).bind("customer", change.customerId).bind("owner", change.ownerId)
                .bind("revision", change.revision).bind("type", change.type).bind("occurred", change.occurredAt.atOffset(ZoneOffset.UTC))
                .fetch().rowsUpdated().then(db.sql("""UPDATE customer_audit_cursor SET revision=:revision, updated_at=CURRENT_TIMESTAMP,
                    deleted_at=CASE WHEN :type='customer.deleted.v1' THEN CURRENT_TIMESTAMP ELSE NULL END WHERE customer_id=:id""")
                    .bind("revision", change.revision).bind("type", change.type).bind("id", change.customerId)
                    .fetch().rowsUpdated()).thenReturn(true)
        }
    }

    override fun purge() = db.sql("DELETE FROM customer_audit WHERE recorded_at < CURRENT_TIMESTAMP - INTERVAL '30 days'")
        .fetch().rowsUpdated().then(db.sql("DELETE FROM customer_audit_cursor WHERE deleted_at < CURRENT_TIMESTAMP - INTERVAL '31 days'")
            .fetch().rowsUpdated()).then()
}
