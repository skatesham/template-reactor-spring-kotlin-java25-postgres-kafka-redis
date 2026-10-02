package com.kotlin.template.identity.domain.model

import java.util.*

class User(
    val id: UUID,
    val name: String,
    val email: String,
    val passwordHash: String,
    roles: Set<Role>,
) {
    val roles: Set<Role> = roles.toSet()

    init {
        require(name.isNotBlank() && name.length <= 100)
        require(email.isNotBlank() && email.length <= 254 && email == normalizeEmail(email))
        require(passwordHash.isNotBlank())
        require(this.roles.isNotEmpty())
    }

    companion object {
        fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)
        fun register(name: String, email: String, passwordHash: String): User =
            User(UUID.randomUUID(), name.trim(), normalizeEmail(email), passwordHash, setOf(Role.USER))
    }
}
