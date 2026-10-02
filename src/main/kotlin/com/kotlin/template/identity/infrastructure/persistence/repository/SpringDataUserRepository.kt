package com.kotlin.template.identity.infrastructure.persistence.repository

import com.kotlin.template.identity.infrastructure.persistence.entity.UserJpaEntity
import java.util.*
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataUserRepository : JpaRepository<UserJpaEntity, UUID> {
    fun findByEmail(email: String): UserJpaEntity?
}
