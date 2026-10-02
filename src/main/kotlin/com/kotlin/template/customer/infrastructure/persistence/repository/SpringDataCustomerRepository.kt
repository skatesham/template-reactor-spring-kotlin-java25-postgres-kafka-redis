package com.kotlin.template.customer.infrastructure.persistence.repository

import com.kotlin.template.customer.infrastructure.persistence.entity.CustomerJpaEntity
import jakarta.persistence.LockModeType
import java.util.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface SpringDataCustomerRepository : JpaRepository<CustomerJpaEntity, UUID> {
    fun findByIdAndOwnerId(id: UUID, ownerId: UUID): CustomerJpaEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CustomerJpaEntity c where c.id = :id and c.ownerId = :ownerId")
    fun lockByIdAndOwnerId(id: UUID, ownerId: UUID): CustomerJpaEntity?
}
