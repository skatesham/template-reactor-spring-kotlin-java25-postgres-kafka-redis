package com.kotlin.template.customer.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.*

@Entity
@Table(name = "customers")
class CustomerJpaEntity(
    @Id val id: UUID,
    @Column(name = "owner_id", nullable = false) val ownerId: UUID,
    @Column(nullable = false, length = 100) var name: String,
    @Column(nullable = false, length = 254) var email: String,
    @Column(nullable = false) var revision: Long,
    @Column(name = "created_at", nullable = false) val createdAt: Instant,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant,
)
