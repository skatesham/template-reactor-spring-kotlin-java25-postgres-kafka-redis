package com.kotlin.template.identity.interfaces.rest

import com.kotlin.template.identity.application.exception.EmailAlreadyRegistered
import com.kotlin.template.identity.application.exception.InvalidCredentials
import com.kotlin.template.identity.application.exception.UserNotFound
import org.springframework.http.*
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestControllerAdvice
class IdentityExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(EmailAlreadyRegistered::class)
    fun duplicateEmail(): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Email já cadastrado.")

    @ExceptionHandler(InvalidCredentials::class)
    fun invalidCredentials(): ResponseEntity<ProblemDetail> = ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Email ou senha inválidos."))

    @ExceptionHandler(UserNotFound::class)
    fun userNotFound(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Usuário não encontrado.")

    override fun handleWebExchangeBindException(
        ex: WebExchangeBindException, headers: HttpHeaders, status: HttpStatusCode, request: ServerWebExchange,
    ): Mono<ResponseEntity<Any>> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Verifique os campos informados.")
        problem.setProperty("errors", ex.bindingResult.fieldErrors.map {
            mapOf("field" to it.field, "message" to (it.defaultMessage ?: "Valor inválido"))
        })
        return handleExceptionInternal(ex, problem, headers, status, request)
    }
}
