package com.kotlin.template.customer.domain.model

import com.kotlin.template.customer.domain.event.CustomerChangeKind
import com.kotlin.template.customer.domain.event.CustomerChanged
import com.kotlin.template.customer.domain.exception.CustomerRevisionConflict
import java.time.Instant
import java.util.*

class Customer(
    val id: CustomerId,
    val ownerId: UUID,
    name: String,
    email: CustomerEmail,
    revision: Long,
    val createdAt: Instant,
    updatedAt: Instant,
) {
    var name: String = name; private set
    var email: CustomerEmail = email; private set
    var revision: Long = revision; private set
    var updatedAt: Instant = updatedAt; private set
    private var deleted = false
    private val changes = mutableListOf<CustomerChanged>()
    val events: List<CustomerChanged> get() = changes.toList()

    init {
        validateName(name); require(revision > 0)
    }

    fun update(name: String, email: String, expectedRevision: Long, eventId: UUID, now: Instant) {
        check(!deleted) { "Customer already deleted" }
        checkRevision(expectedRevision)
        val normalizedName = name.trim()
        validateName(normalizedName)
        val normalizedEmail = CustomerEmail.of(email)
        this.name = normalizedName
        this.email = normalizedEmail
        revision++
        updatedAt = now
        record(CustomerChangeKind.UPDATED, eventId, now)
    }

    fun delete(expectedRevision: Long, eventId: UUID, now: Instant) {
        check(!deleted) { "Customer already deleted" }
        checkRevision(expectedRevision)
        revision++
        deleted = true
        record(CustomerChangeKind.DELETED, eventId, now)
    }

    private fun checkRevision(expected: Long) {
        if (revision != expected) throw CustomerRevisionConflict()
    }

    private fun record(kind: CustomerChangeKind, eventId: UUID, now: Instant) {
        changes.add(CustomerChanged(eventId, id, ownerId, revision, kind, now))
    }

    companion object {
        private fun validateName(name: String) {
            require(name.isNotBlank() && name.length <= 100)
        }

        fun register(id: CustomerId, ownerId: UUID, name: String, email: String, eventId: UUID, now: Instant) =
            Customer(id, ownerId, name.trim(), CustomerEmail.of(email), 1, now, now).apply {
                record(CustomerChangeKind.CREATED, eventId, now)
            }
    }
}
