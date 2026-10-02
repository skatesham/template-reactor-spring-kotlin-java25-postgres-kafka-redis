package com.kotlin.template.identity.interfaces.rest.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "Credenciais para emitir um token de acesso.")
class LoginRequest(
    @field:NotBlank @field:Email @field:Size(max = 254)
    @field:Schema(
        description = "Email cadastrado, sem distinção entre maiúsculas e minúsculas.",
        example = "maria@example.com",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    val email: String,
    @field:NotBlank @field:Size(min = 8, max = 128)
    @field:Schema(
        description = "Senha cadastrada.",
        format = "password",
        accessMode = Schema.AccessMode.WRITE_ONLY,
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    val password: String,
)
