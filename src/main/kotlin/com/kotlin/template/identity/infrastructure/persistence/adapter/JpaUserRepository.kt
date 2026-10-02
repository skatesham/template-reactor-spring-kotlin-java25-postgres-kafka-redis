package com.kotlin.template.identity.infrastructure.persistence.adapter

import com.kotlin.template.identity.application.exception.EmailAlreadyRegistered
import com.kotlin.template.identity.domain.model.Role
import com.kotlin.template.identity.domain.model.User
import com.kotlin.template.identity.domain.repository.UserRepository
import com.kotlin.template.identity.infrastructure.persistence.entity.RoleJpaEntity
import com.kotlin.template.identity.infrastructure.persistence.entity.UserJpaEntity
import com.kotlin.template.identity.infrastructure.persistence.repository.SpringDataUserRepository
import jakarta.persistence.EntityManager
import java.util.*
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository

@Repository
class JpaUserRepository(
    private val repository: SpringDataUserRepository,
    private val entityManager: EntityManager,
) : UserRepository {
    override fun findByEmail(email: String): User? = repository.findByEmail(email)?.toDomain()
    override fun findById(id: UUID): User? = repository.findById(id).orElse(null)?.toDomain()

    override fun create(user: User): User {
        val entity = UserJpaEntity(
            user.id, user.name, user.email, user.passwordHash,
            user.roles.map { entityManager.getReference(RoleJpaEntity::class.java, it.name) }.toSet()
        )
        try {
            // Flush aqui permite traduzir também a colisão de cadastros concorrentes.
            repository.saveAndFlush(entity)
        } catch (exception: DataIntegrityViolationException) {
            val duplicateEmail = generateSequence<Throwable>(exception) { it.cause }
                .filterIsInstance<ConstraintViolationException>()
                .any { it.constraintName == "uk_users_email" }
            if (duplicateEmail) throw EmailAlreadyRegistered()
            throw exception
        }
        return user
    }

    private fun UserJpaEntity.toDomain() =
        User(id, name, email, passwordHash, roles.map { Role.valueOf(it.name) }.toSet())
}
