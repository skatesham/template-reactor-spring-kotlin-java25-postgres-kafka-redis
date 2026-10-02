package com.kotlin.template.customer.interfaces.rest

import com.kotlin.template.customer.application.exception.CustomerCreationConflict
import com.kotlin.template.customer.application.exception.CustomerEmailAlreadyRegistered
import com.kotlin.template.customer.application.exception.CustomerNotFound
import com.kotlin.template.customer.domain.exception.CustomerRevisionConflict
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.transaction.TransactionException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class CustomerExceptionHandler {
    @ExceptionHandler(CustomerNotFound::class)
    fun missing() = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Customer não encontrado.")

    @ExceptionHandler(CustomerEmailAlreadyRegistered::class)
    fun duplicate() = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Email já cadastrado para este usuário.")

    @ExceptionHandler(CustomerRevisionConflict::class)
    fun revision() =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Customer foi alterado. Consulte a revisão atual.")

    @ExceptionHandler(CustomerCreationConflict::class)
    fun creationConflict() = ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT,
        "Chave de criação já utilizada. Consulte o Customer ou use outra chave."
    )

    @ExceptionHandler(
        DataAccessException::class,
        TransactionException::class,
        ExecutionException::class,
        TimeoutException::class
    )
    fun unavailable() =
        ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Serviço temporariamente indisponível.")

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid() = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Verifique os campos informados.")
}
