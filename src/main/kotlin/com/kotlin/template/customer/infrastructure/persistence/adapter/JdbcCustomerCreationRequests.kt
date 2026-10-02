package com.kotlin.template.customer.infrastructure.persistence.adapter

import com.kotlin.template.customer.application.exception.CustomerCreationConflict
import com.kotlin.template.customer.application.port.CustomerCreationRequests
import java.security.MessageDigest
import java.util.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class JdbcCustomerCreationRequests(private val jdbc: JdbcTemplate) : CustomerCreationRequests {
    override fun reserve(ownerId: UUID, key: UUID, name: String, email: String): UUID? {
        // Length prefix prevents ambiguous concatenation; no request body retained.
        val digest = MessageDigest.getInstance("SHA-256").digest("${name.length}:$name$email".toByteArray())
        jdbc.update(
            """INSERT INTO customer_creation_requests(owner_id, request_key, fingerprint)
            VALUES (?, ?, ?) ON CONFLICT DO NOTHING""", ownerId, key, digest
        )
        return jdbc.query(
            "SELECT fingerprint, customer_id FROM customer_creation_requests WHERE owner_id=? AND request_key=? FOR UPDATE",
            { rs, _ ->
                if (!MessageDigest.isEqual(digest, rs.getBytes("fingerprint"))) throw CustomerCreationConflict()
                rs.getObject("customer_id", UUID::class.java)
            }, ownerId, key
        ).single()
    }

    override fun complete(ownerId: UUID, key: UUID, customerId: UUID) {
        jdbc.update(
            "UPDATE customer_creation_requests SET customer_id=? WHERE owner_id=? AND request_key=?",
            customerId,
            ownerId,
            key
        )
    }

    override fun purge() {
        jdbc.update("DELETE FROM customer_creation_requests WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '1 day'")
    }
}
