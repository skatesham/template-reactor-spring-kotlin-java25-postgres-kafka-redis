package com.kotlin.template.customer

import com.fasterxml.uuid.Generators
import com.kotlin.template.customer.application.contract.CustomerChange
import com.kotlin.template.customer.application.exception.CustomerNotFound
import com.kotlin.template.customer.application.port.*
import com.kotlin.template.customer.application.port.CustomerCache
import com.kotlin.template.customer.application.port.CustomerCreationRequests
import com.kotlin.template.customer.application.port.CustomerEventPublisher
import com.kotlin.template.customer.application.port.CustomerIds
import com.kotlin.template.customer.application.port.CustomerOutbox
import com.kotlin.template.customer.application.port.PendingCustomerChange
import com.kotlin.template.customer.application.result.CustomerDetails
import com.kotlin.template.customer.application.usecase.create.CreateCustomer
import com.kotlin.template.customer.application.usecase.create.CreateCustomerCommand
import com.kotlin.template.customer.application.usecase.delivery.PublishCustomerOutbox
import com.kotlin.template.customer.application.usecase.find.FindCustomer
import com.kotlin.template.customer.application.usecase.find.FindCustomerQuery
import com.kotlin.template.customer.domain.model.Customer
import com.kotlin.template.customer.domain.model.CustomerId
import com.kotlin.template.customer.domain.repository.CustomerRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class CustomerApplicationTests {
    private val now = Instant.now()
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val generator = Generators.timeBasedEpochGenerator()
    private val ids = CustomerIds { generator.generate() }

    @Test
    fun `create records profile and outbox and replay of same request creates no new event`() {
        val repository = MemoryCustomers()
        val outbox = MemoryOutbox()
        val requests = MemoryRequests()
        val create = CreateCustomer(repository, outbox, ids, clock, requests)
        val command =
            CreateCustomerCommand(UUID.randomUUID(), "Synthetic Customer", "synthetic@example.com", UUID.randomUUID())
        val first = create.execute(command)
        assertEquals(first, create.execute(command))
        assertEquals(1, repository.values.size)
        assertEquals(1, outbox.values.size)
        assertEquals(first.id, outbox.values.single().customerId)
    }

    @Test
    fun `cache hit still requires ownership and source revision`() {
        val repository = MemoryCustomers()
        val customer = Customer.register(
            CustomerId(ids.next()),
            UUID.randomUUID(),
            "Synthetic Customer",
            "synthetic@example.com",
            ids.next(),
            now
        )
        repository.create(customer)
        val cache = MemoryCache()
        val find = FindCustomer(repository, cache)
        val query = FindCustomerQuery(customer.id.value, customer.ownerId)
        assertEquals(find.execute(query), find.execute(query))
        assertEquals(1, cache.puts)
        assertFailsWith<CustomerNotFound> { find.execute(query.copy(ownerId = UUID.randomUUID())) }
        repository.delete(customer)
        assertFailsWith<CustomerNotFound> { find.execute(query) }
    }

    @Test
    fun `publication failures persist bounded exponential retry and exhaustion without publishing success`() {
        val outbox = MemoryOutbox()
        val customer = Customer.register(
            CustomerId(ids.next()),
            UUID.randomUUID(),
            "Synthetic Customer",
            "synthetic@example.com",
            ids.next(),
            now
        )
        outbox.append(CustomerChange.from(customer.events.single()))
        val publisher = PublishCustomerOutbox(
            outbox,
            CustomerEventPublisher { throw IllegalStateException("broker offline") },
            clock,
            SimpleMeterRegistry(),
            3
        )
        repeat(3) { assertTrue(publisher.execute()) }
        assertEquals(listOf(2L, 4L, 8L), outbox.retries.map { Duration.between(now, it).seconds })
        assertEquals(3, outbox.attempts)
        assertTrue(outbox.exhausted)
        assertFalse(outbox.published)
    }

    @Test
    fun `publisher waits for acknowledgment before marking published`() {
        val outbox = MemoryOutbox()
        val customer = Customer.register(
            CustomerId(ids.next()),
            UUID.randomUUID(),
            "Synthetic Customer",
            "synthetic@example.com",
            ids.next(),
            now
        )
        outbox.append(CustomerChange.from(customer.events.single()))
        var observed = false
        val publisher = PublishCustomerOutbox(
            outbox,
            CustomerEventPublisher { assertFalse(outbox.published); observed = true },
            clock,
            SimpleMeterRegistry(),
            3
        )
        assertTrue(publisher.execute())
        assertTrue(observed); assertTrue(outbox.published)
    }

    class MemoryCustomers : CustomerRepository {
        val values = mutableMapOf<UUID, Customer>()
        override fun create(customer: Customer) {
            values[customer.id.value] = customer
        }

        override fun find(id: CustomerId, ownerId: UUID) = values[id.value]?.takeIf { it.ownerId == ownerId }
        override fun findForUpdate(id: CustomerId, ownerId: UUID) = find(id, ownerId)
        override fun lockRevision(id: CustomerId, ownerId: UUID) = find(id, ownerId)?.revision
        override fun save(customer: Customer) {
            values[customer.id.value] = customer
        }

        override fun delete(customer: Customer) {
            values.remove(customer.id.value)
        }

        override fun list(ownerId: UUID, after: UUID?, limit: Int) =
            values.values.filter { it.ownerId == ownerId }.take(limit)

        override fun expired(before: Instant, limit: Int) = emptyList<Pair<CustomerId, UUID>>()
    }

    class MemoryRequests : CustomerCreationRequests {
        var id: UUID? = null
        override fun reserve(ownerId: UUID, key: UUID, name: String, email: String) = id
        override fun complete(ownerId: UUID, key: UUID, customerId: UUID) {
            id = customerId
        }

        override fun purge() {}
    }

    class MemoryCache : CustomerCache {
        val values = mutableMapOf<Pair<UUID, Long>, CustomerDetails>()
        var puts = 0
        override fun get(id: UUID, revision: Long) = values[id to revision]
        override fun put(details: CustomerDetails) {
            puts++; values[details.id to details.revision] = details
        }

        override fun evictAfterCommit(id: UUID, revision: Long) {
            values.remove(id to revision)
        }
    }

    class MemoryOutbox : CustomerOutbox {
        val values = mutableListOf<CustomerChange>()
        val retries = mutableListOf<Instant>()
        var attempts = 0;
        var exhausted = false;
        var published = false
        override fun append(change: CustomerChange) {
            values.add(change)
        }

        override fun lockNext() = values.firstOrNull()?.let { PendingCustomerChange(it, attempts) }
        override fun published(id: UUID) {
            published = true
        }

        override fun failed(id: UUID, attempts: Int, nextAttempt: Instant, exhausted: Boolean) {
            this.attempts = attempts; this.exhausted = exhausted; retries.add(nextAttempt)
        }

        override fun purge(before: Instant) {}
        override fun findPublished(id: UUID) = values.find { it.eventId == id }
        override fun retryFailed(id: UUID) = true
    }
}
