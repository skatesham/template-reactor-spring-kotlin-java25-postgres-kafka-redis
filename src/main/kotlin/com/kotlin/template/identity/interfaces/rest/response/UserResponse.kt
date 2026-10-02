package com.kotlin.template.identity.interfaces.rest.response

import com.kotlin.template.identity.application.result.UserDetails
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

@Schema(description = "Dados públicos do usuário, sem senha ou hash.")
data class UserResponse(
    @field:Schema(description = "Identificador técnico do usuário.", format = "uuid") val id: UUID,
    @field:Schema(description = "Nome do usuário.", example = "Maria Exemplo") val name: String,
    @field:Schema(description = "Email normalizado.", example = "maria@example.com") val email: String,
    @field:Schema(description = "Permissões atribuídas pelo servidor.", example = "[\"USER\"]") val roles: Set<String>,
) {
    companion object {
        fun from(user: UserDetails) = UserResponse(user.id, user.name, user.email, user.roles)
    }
}
