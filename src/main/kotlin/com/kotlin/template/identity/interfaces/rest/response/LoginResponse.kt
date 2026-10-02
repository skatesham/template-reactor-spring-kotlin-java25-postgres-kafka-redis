package com.kotlin.template.identity.interfaces.rest.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Token de acesso utilizado no header Authorization: Bearer <accessToken>.")
data class LoginResponse(
    @field:Schema(description = "JWT assinado. Não inclui email nem nome.") val accessToken: String,
    @field:Schema(description = "Tipo de autenticação.", example = "Bearer") val tokenType: String = "Bearer",
    @field:Schema(description = "Validade do token em segundos.", example = "900") val expiresIn: Long,
)
