package com.kotlin.template.identity.application.port

import com.kotlin.template.identity.domain.model.User
import java.util.UUID
import reactor.core.publisher.Mono

interface UserRepository {

    fun findByEmail(email: String): Mono<User>

    fun findById(id: UUID): Mono<User>

    fun create(user: User): Mono<User>
}
