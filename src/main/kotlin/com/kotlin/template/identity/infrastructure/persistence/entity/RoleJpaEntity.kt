package com.kotlin.template.identity.infrastructure.persistence.entity

import jakarta.persistence.*

@Entity
@Table(name = "roles")
class RoleJpaEntity(@Id @Column(length = 32) val name: String)
