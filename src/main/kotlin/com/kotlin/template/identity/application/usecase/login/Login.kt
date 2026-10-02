package com.kotlin.template.identity.application.usecase.login

import com.kotlin.template.identity.application.exception.InvalidCredentials
import com.kotlin.template.identity.application.port.AccessToken
import com.kotlin.template.identity.application.port.AccessTokenIssuer
import com.kotlin.template.identity.application.port.PasswordHasher
import com.kotlin.template.identity.domain.model.User
import com.kotlin.template.identity.domain.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class Login(
    private val users: UserRepository,
    private val passwords: PasswordHasher,
    private val tokens: AccessTokenIssuer,
) {
    private val dummyHash = passwords.hash("dummy-password-for-timing-comparison")

    @Transactional(readOnly = true)
    fun execute(email: String, password: String): AccessToken {
        val user = users.findByEmail(User.normalizeEmail(email))
        val matches = passwords.matches(password, user?.passwordHash ?: dummyHash)
        if (user == null || !matches) throw InvalidCredentials()
        return tokens.issue(user)
    }
}
