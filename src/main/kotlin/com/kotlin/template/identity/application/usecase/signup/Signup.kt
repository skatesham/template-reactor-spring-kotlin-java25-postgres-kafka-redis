package com.kotlin.template.identity.application.usecase.signup

import com.kotlin.template.identity.application.exception.EmailAlreadyRegistered
import com.kotlin.template.identity.application.port.PasswordHasher
import com.kotlin.template.identity.application.port.UserRepository
import com.kotlin.template.identity.application.result.UserDetails
import com.kotlin.template.identity.domain.model.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

@Service
class Signup(private val users: UserRepository, private val passwords: PasswordHasher) {

    @Transactional
    fun execute(name: String, email: String, password: String): Mono<UserDetails> = Mono.defer {
        val normalizedEmail = User.normalizeEmail(email)
        users.findByEmail(normalizedEmail).hasElement().flatMap { exists ->
            if (exists) Mono.error(EmailAlreadyRegistered()) else passwords.hash(password)
                .flatMap { hash -> users.create(User.register(name, normalizedEmail, hash)) }.map(UserDetails::from)
        }
    }
}
