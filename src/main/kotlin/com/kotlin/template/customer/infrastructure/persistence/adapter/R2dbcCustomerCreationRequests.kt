package com.kotlin.template.customer.infrastructure.persistence.adapter

import com.kotlin.template.customer.application.exception.CustomerCreationConflict
import com.kotlin.template.customer.application.port.CustomerCreationRequests
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
class R2dbcCustomerCreationRequests(private val db: DatabaseClient) : CustomerCreationRequests {

    override fun reserve(ownerId: UUID, key: UUID, name: String, email: String): Mono<UUID> = Mono.defer {
        val digest = MessageDigest.getInstance("SHA-256").digest("${name.length}:$name$email".toByteArray())
        db.sql("INSERT INTO customer_creation_requests(owner_id, request_key, fingerprint) VALUES (:owner, :key, :digest) ON CONFLICT DO NOTHING")
            .bind("owner", ownerId).bind("key", key).bind("digest", digest).fetch().rowsUpdated()
            .then(db.sql("SELECT fingerprint, customer_id FROM customer_creation_requests WHERE owner_id=:owner AND request_key=:key FOR UPDATE")
                .bind("owner", ownerId).bind("key", key).map { row, _ ->
                    val buffer = row.get("fingerprint", ByteBuffer::class.java)!!
                    val stored = ByteArray(buffer.remaining()).also { buffer.get(it) }
                    if (!MessageDigest.isEqual(digest, stored)) throw CustomerCreationConflict()
                    java.util.Optional.ofNullable(row.get("customer_id", UUID::class.java))
                }.one()).flatMap { Mono.justOrEmpty(it) }
    }

    override fun complete(ownerId: UUID, key: UUID, customerId: UUID) = db.sql(
        "UPDATE customer_creation_requests SET customer_id=:id WHERE owner_id=:owner AND request_key=:key"
    ).bind("id", customerId).bind("owner", ownerId).bind("key", key).fetch().rowsUpdated().then()

    override fun purge() = db.sql("DELETE FROM customer_creation_requests WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '1 day'")
        .fetch().rowsUpdated().then()
}
