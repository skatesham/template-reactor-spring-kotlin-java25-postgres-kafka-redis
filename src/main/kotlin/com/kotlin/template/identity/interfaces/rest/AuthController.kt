package com.kotlin.template.identity.interfaces.rest

import com.kotlin.template.identity.application.usecase.login.Login
import com.kotlin.template.identity.application.usecase.signup.Signup
import com.kotlin.template.identity.interfaces.rest.request.LoginRequest
import com.kotlin.template.identity.interfaces.rest.request.SignupRequest
import com.kotlin.template.identity.interfaces.rest.response.LoginResponse
import com.kotlin.template.identity.interfaces.rest.response.UserResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "Cadastro e login públicos.")
class AuthController(private val signup: Signup, private val login: Login) {
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(
        summary = "Cadastrar usuário",
        description = "Cria um usuário com role USER. Não aceita roles no payload. Faça login após o cadastro."
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Usuário cadastrado"),
        ApiResponse(
            responseCode = "400",
            description = "Payload inválido",
            content = [Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class)
            )]
        ),
        ApiResponse(
            responseCode = "409",
            description = "Email já cadastrado",
            content = [Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class)
            )]
        ),
    )
    fun signup(@Valid @RequestBody request: SignupRequest): Mono<UserResponse> =
        signup.execute(request.name, request.email, request.password).map(UserResponse::from)

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(
        summary = "Autenticar usuário",
        description = "Valida email e senha e emite um JWT. Credenciais incorretas retornam a mesma mensagem para email inexistente e senha inválida."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Autenticado"),
        ApiResponse(
            responseCode = "400",
            description = "Payload inválido",
            content = [Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class)
            )]
        ),
        ApiResponse(
            responseCode = "401",
            description = "Credenciais inválidas",
            content = [Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class)
            )]
        ),
    )
    fun login(@Valid @RequestBody request: LoginRequest): Mono<LoginResponse> =
        login.execute(request.email, request.password).map { LoginResponse(it.value, expiresIn = it.expiresIn) }
}
