package com.kotlin.template.identity.application.result

import com.kotlin.template.identity.domain.model.User
import java.util.*

data class UserDetails(val id: UUID, val name: String, val email: String, val roles: Set<String>) {
    companion object {
        fun from(user: User) = UserDetails(user.id, user.name, user.email, user.roles.map { it.name }.toSet())
    }
}
