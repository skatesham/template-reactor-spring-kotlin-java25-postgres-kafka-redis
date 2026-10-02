package com.kotlin.template.identity.application.usecase.login

import com.kotlin.template.identity.application.exception.InvalidCredentials
import com.kotlin.template.identity.application.result.AccessToken
import com.kotlin.template.identity.application.port.AccessTokenIssuer
import com.kotlin.template.identity.application.port.PasswordHasher
import com.kotlin.template.identity.application.port.UserRepository
import com.kotlin.template.identity.domain.model.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

@Service
class Login(
    private val users: UserRepository,
    private val passwords: PasswordHasher,
    private val tokens: AccessTokenIssuer,
) {
    private val dummyHash = passwords.hash("dummy-password-for-timing-comparison").cache()

    @Transactional(readOnly = true)
    fun execute(email: String, password: String): Mono<AccessToken> = Mono.defer {
        users.findByEmail(User.normalizeEmail(email)).flatMap { user ->
            passwords.matches(password, user.passwordHash).flatMap { matches ->
                if (matches) Mono.fromCallable { tokens.issue(user) } else Mono.error(InvalidCredentials())
            }
        }.switchIfEmpty(dummyHash.flatMap { hash -> passwords.matches(password, hash) }
            .then(Mono.error(InvalidCredentials())))
    }
}
