package com.kotlin.template.customer

import com.fasterxml.uuid.Generators
import com.kotlin.template.customer.application.contract.CustomerChange
import com.kotlin.template.customer.domain.event.CustomerChangeKind
import com.kotlin.template.customer.domain.exception.CustomerRevisionConflict
import com.kotlin.template.customer.domain.model.Customer
import com.kotlin.template.customer.domain.model.CustomerId
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class CustomerDomainTests {
    private val generator = Generators.timeBasedEpochGenerator()
    private val now = Instant.now()
    private fun customer() = Customer.register(
        CustomerId(generator.generate()), UUID.randomUUID(), " Synthetic Customer ",
        "CONTACT@EXAMPLE.COM", generator.generate(), now
    )

    @Test
    fun `aggregate normalizes validates and records minimal ordered facts`() {
        val customer = customer()
        assertEquals("Synthetic Customer", customer.name)
        assertEquals("contact@example.com", customer.email.value)
        customer.update("Updated Customer", "NEW@example.com", 1, generator.generate(), now.plusSeconds(1))
        customer.delete(2, generator.generate(), now.plusSeconds(2))
        assertEquals(listOf(1L, 2L, 3L), customer.events.map { it.revision })
        assertEquals(
            listOf(CustomerChangeKind.CREATED, CustomerChangeKind.UPDATED, CustomerChangeKind.DELETED),
            customer.events.map { it.kind })
        assertEquals(7, customer.id.value.version())
        customer.events.forEach { assertEquals(7, it.eventId.version()) }
        assertEquals(3, customer.events.size)
        assertFailsWith<IllegalStateException> { customer.delete(3, generator.generate(), now.plusSeconds(3)) }
        assertFailsWith<IllegalStateException> {
            customer.update(
                "Resurrect",
                "resurrect@example.com",
                3,
                generator.generate(),
                now.plusSeconds(3)
            )
        }
    }

    @Test
    fun `invalid change and stale revision never mutate aggregate`() {
        val customer = customer()
        assertFailsWith<IllegalArgumentException> {
            customer.update(
                " ",
                "new@example.com",
                1,
                generator.generate(),
                now
            )
        }
        assertFailsWith<IllegalArgumentException> { customer.update("Updated", "bad", 1, generator.generate(), now) }
        assertFailsWith<CustomerRevisionConflict> {
            customer.update(
                "Updated",
                "new@example.com",
                2,
                generator.generate(),
                now
            )
        }
        assertFailsWith<CustomerRevisionConflict> { customer.delete(9, generator.generate(), now) }
        assertEquals(1, customer.revision)
        assertEquals("Synthetic Customer", customer.name)
        assertEquals(1, customer.events.size)
    }

    @Test
    fun `integration contract rejects unknown schemas kinds malformed sequencing and expired facts`() {
        val event = CustomerChange.from(customer().events.single())
        event.validate(now)
        assertFailsWith<IllegalArgumentException> { event.copy(schemaVersion = 2).validate(now) }
        assertFailsWith<IllegalArgumentException> { event.copy(type = "customer.unknown.v1").validate(now) }
        assertFailsWith<IllegalArgumentException> { event.copy(revision = 2).validate(now) }
        assertFailsWith<IllegalArgumentException> { event.copy(eventId = UUID.randomUUID()).validate(now) }
        assertFailsWith<IllegalArgumentException> {
            event.copy(occurredAt = now.minusSeconds(31 * 86400L)).validate(now)
        }
    }
}
