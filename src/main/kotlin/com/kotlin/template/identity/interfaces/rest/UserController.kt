package com.kotlin.template.identity.interfaces.rest

import com.kotlin.template.identity.application.usecase.currentuser.CurrentUser
import com.kotlin.template.identity.interfaces.rest.response.UserResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import java.util.*
import org.springframework.http.ProblemDetail
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
@Tag(name = "Usuários", description = "Dados do usuário autenticado.")
class UserController(private val currentUser: CurrentUser) {
    @GetMapping("/me")
    @Operation(
        summary = "Consultar meu usuário",
        description = "Retorna apenas o usuário identificado pelo JWT, sem receber um ID na rota."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Usuário autenticado"),
        ApiResponse(
            responseCode = "401",
            description = "Token ausente, inválido ou expirado",
            content = [Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class)
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Usuário não existe mais",
            content = [Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class)
            )]
        ),
    )
    fun me(@AuthenticationPrincipal jwt: Jwt): UserResponse =
        UserResponse.from(currentUser.execute(UUID.fromString(jwt.subject)))
}
