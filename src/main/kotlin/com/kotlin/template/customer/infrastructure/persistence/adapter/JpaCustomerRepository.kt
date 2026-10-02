package com.kotlin.template.customer.infrastructure.persistence.adapter

import com.kotlin.template.customer.application.exception.CustomerEmailAlreadyRegistered
import com.kotlin.template.customer.domain.model.Customer
import com.kotlin.template.customer.domain.model.CustomerEmail
import com.kotlin.template.customer.domain.model.CustomerId
import com.kotlin.template.customer.domain.repository.CustomerRepository
import com.kotlin.template.customer.infrastructure.persistence.entity.CustomerJpaEntity
import com.kotlin.template.customer.infrastructure.persistence.repository.SpringDataCustomerRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class JpaCustomerRepository(
    private val repository: SpringDataCustomerRepository,
    private val jdbc: JdbcTemplate,
    private val meters: MeterRegistry
) : CustomerRepository {
    override fun list(ownerId: UUID, after: UUID?, limit: Int): List<Customer> = jdbc.query(
        "SELECT * FROM customers WHERE owner_id=? AND (?::uuid IS NULL OR id > ?::uuid) ORDER BY id LIMIT ?",
        { rs, _ ->
            Customer(
                CustomerId(rs.getObject("id", UUID::class.java)), rs.getObject("owner_id", UUID::class.java),
                rs.getString("name"), CustomerEmail(rs.getString("email")), rs.getLong("revision"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()
            )
        }, ownerId, after, after, limit
    )

    override fun create(customer: Customer) {
        observed("create") { translateDuplicate { repository.saveAndFlush(customer.toJpa()) } }
    }

    override fun find(id: CustomerId, ownerId: UUID) =
        observed("find") { repository.findByIdAndOwnerId(id.value, ownerId)?.toDomain() }

    override fun findForUpdate(id: CustomerId, ownerId: UUID) =
        observed("lock") { repository.lockByIdAndOwnerId(id.value, ownerId)?.toDomain() }

    override fun lockRevision(id: CustomerId, ownerId: UUID): Long? = observed("cache-revision") {
        jdbc.query(
            "SELECT revision FROM customers WHERE id = ? AND owner_id = ? FOR SHARE",
            { rs, _ -> rs.getLong("revision") }, id.value, ownerId
        ).firstOrNull()
    }

    override fun save(customer: Customer) {
        observed("update") {
            val entity = repository.findById(customer.id.value).orElseThrow()
            entity.name = customer.name; entity.email = customer.email.value
            entity.revision = customer.revision; entity.updatedAt = customer.updatedAt
            translateDuplicate { repository.flush() }
        }
    }

    override fun delete(customer: Customer) {
        observed("delete") { repository.deleteById(customer.id.value); repository.flush() }
    }

    override fun expired(before: Instant, limit: Int): List<Pair<CustomerId, UUID>> = jdbc.query(
        "SELECT id, owner_id FROM customers WHERE updated_at < ? ORDER BY updated_at LIMIT ?",
        { rs, _ -> CustomerId(rs.getObject("id", UUID::class.java)) to rs.getObject("owner_id", UUID::class.java) },
        Timestamp.from(before), limit
    )

    private fun <T> observed(operation: String, action: () -> T): T {
        val sample = Timer.start(meters)
        try {
            return action()
        } catch (exception: Exception) {
            meters.counter("customer.persistence.failures", "operation", operation).increment()
            throw exception
        } finally {
            sample.stop(meters.timer("customer.persistence.duration", "operation", operation))
        }
    }

    private fun translateDuplicate(action: () -> Unit) {
        try {
            action()
        } catch (exception: DataIntegrityViolationException) {
            if (generateSequence<Throwable>(exception) { it.cause }.filterIsInstance<ConstraintViolationException>()
                    .any { it.constraintName == "uk_customers_owner_email" }
            ) throw CustomerEmailAlreadyRegistered()
            throw exception
        }
    }

    private fun Customer.toJpa() =
        CustomerJpaEntity(id.value, ownerId, name, email.value, revision, createdAt, updatedAt)

    private fun CustomerJpaEntity.toDomain() =
        Customer(CustomerId(id), ownerId, name, CustomerEmail(email), revision, createdAt, updatedAt)
}
