package com.kotlin.template.identity.infrastructure.security

import com.kotlin.template.identity.application.port.PasswordHasher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class SpringPasswordHasher(private val encoder: PasswordEncoder) : PasswordHasher {
    override fun hash(password: String): String =
        checkNotNull(encoder.encode(password)) { "Password encoder returned no hash" }

    override fun matches(password: String, hash: String): Boolean = encoder.matches(password, hash)
}
