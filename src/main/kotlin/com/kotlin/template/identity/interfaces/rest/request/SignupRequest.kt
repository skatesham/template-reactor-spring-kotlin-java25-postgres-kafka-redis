package com.kotlin.template.identity.interfaces.rest.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "Cadastro de usuário. A role USER é atribuída automaticamente.")
class SignupRequest(
    @field:NotBlank @field:Size(max = 100)
    @field:Schema(
        description = "Nome do usuário, de 1 a 100 caracteres.",
        example = "Maria Exemplo",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    val name: String,
    @field:NotBlank @field:Email @field:Size(max = 254)
    @field:Schema(
        description = "Email único, convertido para minúsculas.",
        example = "maria@example.com",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    val email: String,
    @field:NotBlank @field:Size(min = 8, max = 128)
    @field:Schema(
        description = "Senha de 8 a 128 caracteres, sem remoção de espaços.",
        format = "password",
        accessMode = Schema.AccessMode.WRITE_ONLY,
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    val password: String,
)
