package com.kotlin.template.customer.infrastructure.persistence.adapter

import com.kotlin.template.customer.application.exception.CustomerEmailAlreadyRegistered
import com.kotlin.template.customer.application.port.CustomerRepository
import com.kotlin.template.customer.domain.model.Customer
import com.kotlin.template.customer.domain.model.CustomerEmail
import com.kotlin.template.customer.domain.model.CustomerId
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.r2dbc.postgresql.api.PostgresqlException
import io.r2dbc.spi.Row
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
class R2dbcCustomerRepository(private val db: DatabaseClient, private val meters: MeterRegistry) : CustomerRepository {

    override fun list(ownerId: UUID, after: UUID?, limit: Int) = db.sql(
        "SELECT * FROM customers WHERE owner_id=:owner AND (:after::uuid IS NULL OR id > :after::uuid) ORDER BY id LIMIT :limit"
    ).bind("owner", ownerId).let { if (after == null) it.bindNull("after", UUID::class.java) else it.bind("after", after) }
        .bind("limit", limit).map { row, _ -> customer(row) }.all()

    override fun create(customer: Customer): Mono<Void> = observed("create") {
        bind(db.sql("""INSERT INTO customers(id, owner_id, name, email, revision, created_at, updated_at)
            VALUES (:id, :owner, :name, :email, :revision, :created, :updated)"""), customer)
            .fetch().rowsUpdated().then()
    }

    override fun find(id: CustomerId, ownerId: UUID) = observed("find") { findRow(id, ownerId, "") }

    override fun findForUpdate(id: CustomerId, ownerId: UUID) = observed("lock") { findRow(id, ownerId, " FOR UPDATE") }

    private fun findRow(id: CustomerId, ownerId: UUID, lock: String) = db.sql(
        "SELECT * FROM customers WHERE id=:id AND owner_id=:owner$lock"
    ).bind("id", id.value).bind("owner", ownerId).map { row, _ -> customer(row) }.one()

    override fun lockRevision(id: CustomerId, ownerId: UUID) = observed("cache-revision") {
        db.sql("SELECT revision FROM customers WHERE id=:id AND owner_id=:owner FOR SHARE")
            .bind("id", id.value).bind("owner", ownerId).map { row, _ -> row.get("revision", Long::class.javaObjectType)!!.toLong() }.one()
    }

    override fun save(customer: Customer): Mono<Void> = observed("update") {
        db.sql("UPDATE customers SET name=:name, email=:email, revision=:revision, updated_at=:updated WHERE id=:id AND owner_id=:owner")
            .bind("name", customer.name).bind("email", customer.email.value).bind("revision", customer.revision)
            .bind("updated", customer.updatedAt.atOffset(ZoneOffset.UTC)).bind("id", customer.id.value)
            .bind("owner", customer.ownerId).fetch().rowsUpdated().then()
    }

    override fun delete(customer: Customer): Mono<Void> = observed("delete") {
        db.sql("DELETE FROM customers WHERE id=:id AND owner_id=:owner").bind("id", customer.id.value)
            .bind("owner", customer.ownerId).fetch().rowsUpdated().then()
    }

    override fun expired(before: Instant, limit: Int) = db.sql(
        "SELECT id, owner_id FROM customers WHERE updated_at < :before ORDER BY updated_at LIMIT :limit"
    ).bind("before", before.atOffset(ZoneOffset.UTC)).bind("limit", limit).map { row, _ ->
        CustomerId(row.get("id", UUID::class.java)!!) to row.get("owner_id", UUID::class.java)!!
    }.all()

    private fun bind(sql: DatabaseClient.GenericExecuteSpec, c: Customer) = sql.bind("id", c.id.value)
        .bind("owner", c.ownerId).bind("name", c.name).bind("email", c.email.value).bind("revision", c.revision)
        .bind("created", c.createdAt.atOffset(ZoneOffset.UTC)).bind("updated", c.updatedAt.atOffset(ZoneOffset.UTC))

    private fun customer(row: Row) = Customer(CustomerId(row.get("id", UUID::class.java)!!),
        row.get("owner_id", UUID::class.java)!!, row.get("name", String::class.java)!!,
        CustomerEmail(row.get("email", String::class.java)!!), row.get("revision", Long::class.javaObjectType)!!.toLong(),
        row.get("created_at", OffsetDateTime::class.java)!!.toInstant(), row.get("updated_at", OffsetDateTime::class.java)!!.toInstant())

    private fun <T : Any> observed(operation: String, action: () -> Mono<T>): Mono<T> = Mono.defer {
        val sample = Timer.start(meters)
        action().onErrorMap { error ->
            if (generateSequence(error) { it.cause }.filterIsInstance<PostgresqlException>()
                .any { it.errorDetails.constraintName.orElse(null) == "uk_customers_owner_email" }) CustomerEmailAlreadyRegistered() else error
        }.doOnError { meters.counter("customer.persistence.failures", "operation", operation).increment() }
            .doFinally { sample.stop(meters.timer("customer.persistence.duration", "operation", operation)) }
    }
}
