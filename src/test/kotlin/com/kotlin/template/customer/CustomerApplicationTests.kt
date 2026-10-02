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
import com.kotlin.template.customer.application.port.CustomerRepository
import com.kotlin.template.customer.application.port.PendingCustomerChange
import com.kotlin.template.customer.application.result.CustomerDetails
import com.kotlin.template.customer.application.usecase.create.CreateCustomer
import com.kotlin.template.customer.application.usecase.create.CreateCustomerCommand
import com.kotlin.template.customer.application.usecase.delivery.PublishCustomerOutbox
import com.kotlin.template.customer.application.usecase.find.FindCustomer
import com.kotlin.template.customer.application.usecase.find.FindCustomerQuery
import com.kotlin.template.customer.domain.model.Customer
import com.kotlin.template.customer.domain.model.CustomerId
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
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

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
        val first = create.execute(command).block()!!
        assertEquals(first, create.execute(command).block()!!)
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
        repository.create(customer).block()
        val cache = MemoryCache()
        val find = FindCustomer(repository, cache)
        val query = FindCustomerQuery(customer.id.value, customer.ownerId)
        assertEquals(find.execute(query).block()!!, find.execute(query).block()!!)
        assertEquals(1, cache.puts)
        assertFailsWith<CustomerNotFound> { find.execute(query.copy(ownerId = UUID.randomUUID())).block()!! }
        repository.delete(customer).block()
        assertFailsWith<CustomerNotFound> { find.execute(query).block()!! }
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
        outbox.append(CustomerChange.from(customer.events.single())).block()
        val publisher = PublishCustomerOutbox(
            outbox,
            CustomerEventPublisher { throw IllegalStateException("broker offline") },
            clock,
            SimpleMeterRegistry(),
            3
        )
        repeat(3) { assertTrue(publisher.execute().block()!!) }
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
        outbox.append(CustomerChange.from(customer.events.single())).block()
        var observed = false
        val publisher = PublishCustomerOutbox(
            outbox,
            CustomerEventPublisher { Mono.fromRunnable { assertFalse(outbox.published); observed = true } },
            clock,
            SimpleMeterRegistry(),
            3
        )
        assertTrue(publisher.execute().block()!!)
        assertTrue(observed); assertTrue(outbox.published)
    }

    @Test
    fun `outbox remains unpublished until asynchronous acknowledgment and cancellation retains it`() {
        val outbox = MemoryOutbox()
        val customer = Customer.register(CustomerId(ids.next()), UUID.randomUUID(), "Synthetic", "synthetic@example.com", ids.next(), now)
        outbox.append(CustomerChange.from(customer.events.single())).block()
        val acknowledgment = reactor.core.publisher.Sinks.empty<Void>()
        val publisher = PublishCustomerOutbox(outbox, CustomerEventPublisher { acknowledgment.asMono() }, clock, SimpleMeterRegistry(), 3)
        StepVerifier.create(publisher.execute()).then { assertFalse(outbox.published) }.thenCancel().verify()
        assertFalse(outbox.published)
        StepVerifier.create(publisher.execute()).then { acknowledgment.tryEmitEmpty() }.expectNext(true).verifyComplete()
        assertTrue(outbox.published)
    }

    class MemoryCustomers : CustomerRepository {
        val values = mutableMapOf<UUID, Customer>()
        override fun create(customer: Customer): Mono<Void> = Mono.fromRunnable { values[customer.id.value] = customer }
        override fun find(id: CustomerId, ownerId: UUID): Mono<Customer> = Mono.defer { Mono.justOrEmpty(values[id.value]?.takeIf { it.ownerId == ownerId }) }
        override fun findForUpdate(id: CustomerId, ownerId: UUID) = find(id, ownerId)
        override fun lockRevision(id: CustomerId, ownerId: UUID) = find(id, ownerId).map { it.revision }
        override fun save(customer: Customer): Mono<Void> = Mono.fromRunnable { values[customer.id.value] = customer }
        override fun delete(customer: Customer): Mono<Void> = Mono.fromRunnable { values.remove(customer.id.value) }
        override fun list(ownerId: UUID, after: UUID?, limit: Int) = Flux.defer {
            Flux.fromIterable(values.values.filter { it.ownerId == ownerId && (after == null || it.id.value > after) }.take(limit))
        }
        override fun expired(before: Instant, limit: Int): Flux<Pair<CustomerId, UUID>> = Flux.empty()
    }
    class MemoryRequests : CustomerCreationRequests {
        var id: UUID? = null
        override fun reserve(ownerId: UUID, key: UUID, name: String, email: String): Mono<UUID> = Mono.defer { Mono.justOrEmpty(id) }
        override fun complete(ownerId: UUID, key: UUID, customerId: UUID): Mono<Void> = Mono.fromRunnable { id = customerId }
        override fun purge(): Mono<Void> = Mono.empty()
    }
    class MemoryCache : CustomerCache {
        val values = mutableMapOf<Pair<UUID, Long>, CustomerDetails>()
        var puts = 0
        override fun get(id: UUID, revision: Long): Mono<CustomerDetails> = Mono.defer { Mono.justOrEmpty(values[id to revision]) }
        override fun put(details: CustomerDetails): Mono<Void> = Mono.fromRunnable { puts++; values[details.id to details.revision] = details }
        override fun evictAfterCommit(id: UUID, revision: Long): Mono<Void> = Mono.fromRunnable { values.remove(id to revision) }
    }
    class MemoryOutbox : CustomerOutbox {
        val values = mutableListOf<CustomerChange>()
        val retries = mutableListOf<Instant>()
        var attempts = 0
        var exhausted = false
        var published = false
        override fun append(change: CustomerChange): Mono<Void> = Mono.fromRunnable { values.add(change) }
        override fun lockNext(): Mono<PendingCustomerChange> = Mono.defer { Mono.justOrEmpty(values.firstOrNull()?.let { PendingCustomerChange(it, attempts) }) }
        override fun published(id: UUID): Mono<Void> = Mono.fromRunnable { published = true }
        override fun failed(id: UUID, attempts: Int, nextAttempt: Instant, exhausted: Boolean): Mono<Void> = Mono.fromRunnable {
            this.attempts = attempts; this.exhausted = exhausted; retries.add(nextAttempt)
        }
        override fun purge(before: Instant): Mono<Void> = Mono.empty()
        override fun findPublished(id: UUID): Mono<CustomerChange> = Mono.defer { Mono.justOrEmpty(values.find { it.eventId == id }) }
        override fun retryFailed(id: UUID) = Mono.just(true)
    }
}
