package com.kotlin.template.customer

import com.fasterxml.uuid.Generators
import com.kotlin.template.customer.application.port.CustomerIds
import com.kotlin.template.customer.application.usecase.delete.DeleteCustomer
import com.kotlin.template.customer.application.usecase.retention.DeleteExpiredCustomers
import com.kotlin.template.customer.domain.model.Customer
import com.kotlin.template.customer.domain.model.CustomerEmail
import com.kotlin.template.customer.domain.model.CustomerId
import com.kotlin.template.customer.domain.repository.CustomerRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CustomerRetentionConcurrencyTests {
    @ParameterizedTest
    @ValueSource(strings = ["removed-before-load", "updated-before-load", "removed-before-lock", "updated-before-lock"])
    fun `retention tolerates concurrent changes without deleting a freshly active profile`(scenario: String) {
        val now = Instant.now()
        val generator = Generators.timeBasedEpochGenerator()
        val id = CustomerId(generator.generate());
        val owner = UUID.randomUUID()
        val expiredAt = now.minusSeconds(366 * 86400L)
        val old = Customer(id, owner, "Synthetic", CustomerEmail.of("synthetic@example.com"), 1, expiredAt, expiredAt)
        val fresh = Customer(id, owner, "Freshly active", CustomerEmail.of("synthetic@example.com"), 2, expiredAt, now)
        val memory = CustomerApplicationTests.MemoryCustomers()
        var locks = 0;
        var deletions = 0
        val repository = object : CustomerRepository by memory {
            override fun expired(before: Instant, limit: Int) = listOf(id to owner)
            override fun find(id: CustomerId, ownerId: UUID): Customer? = when (scenario) {
                "removed-before-load" -> null
                "updated-before-load" -> fresh
                else -> old
            }

            override fun findForUpdate(id: CustomerId, ownerId: UUID): Customer? {
                locks++
                return if (scenario == "removed-before-lock") null else fresh
            }

            override fun delete(customer: Customer) {
                deletions++
            }
        }
        val outbox = CustomerApplicationTests.MemoryOutbox()
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val delete = DeleteCustomer(
            repository,
            outbox,
            CustomerApplicationTests.MemoryCache(),
            CustomerIds { generator.generate() },
            clock
        )
        DeleteExpiredCustomers(repository, delete, clock).execute()
        assertEquals(0, deletions)
        assertTrue(outbox.values.isEmpty())
        assertEquals(if (scenario.endsWith("lock")) 1 else 0, locks)
        assertEquals(2, fresh.revision)
        assertTrue(fresh.events.isEmpty())
    }
}
