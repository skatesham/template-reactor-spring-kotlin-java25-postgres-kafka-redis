package com.kotlin.template.customer.interfaces.rest.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "Dados para cadastrar um customer pertencente ao usuário autenticado.")
data class CreateCustomerRequest(
    @field:NotBlank @field:Size(max = 100)
    @field:Schema(
        description = "Nome com até 100 caracteres. Espaços nas extremidades são removidos.", example = "Cliente Exemplo", requiredMode = Schema.RequiredMode.REQUIRED
    )
    val name: String,
    @field:NotBlank @field:Email @field:Size(max = 254)
    @field:Schema(
        description = "Email único por proprietário, normalizado para minúsculas e sem espaços nas extremidades.", example = "cliente@example.com", requiredMode = Schema.RequiredMode.REQUIRED
    )
    val email: String,
)
