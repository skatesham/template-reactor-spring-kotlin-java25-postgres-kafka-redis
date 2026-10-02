package com.kotlin.template.identity.infrastructure.persistence.entity

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "users")
class UserJpaEntity(
    @Id val id: UUID,
    @Column(nullable = false, length = 100) val name: String,
    @Column(nullable = false, length = 254) val email: String,
    @Column(name = "password_hash", nullable = false, length = 255) val passwordHash: String,
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles", joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_name")]
    )
    val roles: Set<RoleJpaEntity>,
)
