package com.kotlin.template.identity.application.usecase.signup

import com.kotlin.template.identity.application.exception.EmailAlreadyRegistered
import com.kotlin.template.identity.application.port.PasswordHasher
import com.kotlin.template.identity.application.result.UserDetails
import com.kotlin.template.identity.domain.model.User
import com.kotlin.template.identity.domain.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class Signup(private val users: UserRepository, private val passwords: PasswordHasher) {
    @Transactional
    fun execute(name: String, email: String, password: String): UserDetails {
        val normalizedEmail = User.normalizeEmail(email)
        if (users.findByEmail(normalizedEmail) != null) throw EmailAlreadyRegistered()
        return UserDetails.from(users.create(User.register(name, normalizedEmail, passwords.hash(password))))
    }
}
