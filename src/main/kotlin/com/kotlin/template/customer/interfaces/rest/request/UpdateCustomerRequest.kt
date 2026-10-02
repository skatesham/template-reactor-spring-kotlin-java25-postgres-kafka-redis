package com.kotlin.template.customer.interfaces.rest.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

@Schema(description = "Substituição de nome e email com controle de concorrência pela revisão atual.")
data class UpdateCustomerRequest(
    @field:NotBlank @field:Size(max = 100)
    @field:Schema(
        description = "Novo nome com até 100 caracteres. Espaços nas extremidades são removidos.", example = "Cliente Atualizado", requiredMode = Schema.RequiredMode.REQUIRED
    )
    val name: String,
    @field:NotBlank @field:Email @field:Size(max = 254)
    @field:Schema(
        description = "Novo email, único por proprietário e normalizado para minúsculas.", example = "atualizado@example.com", requiredMode = Schema.RequiredMode.REQUIRED
    )
    val email: String,
    @field:Positive
    @field:Schema(
        description = "Revisão retornada pela última consulta. Uma revisão desatualizada retorna 409.", example = "1", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED
    )
    val revision: Long,
)
