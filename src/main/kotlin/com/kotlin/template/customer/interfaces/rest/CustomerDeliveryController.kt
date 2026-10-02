package com.kotlin.template.customer.interfaces.rest

import com.kotlin.template.customer.application.usecase.delivery.RecoverCustomerDelivery
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import java.util.*
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/customer-delivery")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Entrega de customers", description = "Recuperação administrativa da entrega de eventos. Requer role ADMIN.")
class CustomerDeliveryController(private val recover: RecoverCustomerDelivery) {
    @PostMapping("/{eventId}/retry")
    @Operation(
        summary = "Reagendar evento com falha",
        description = "Recoloca um evento da outbox em estado FAILED na fila de publicação. Requer ADMIN. O processamento e a entrega ocorrerão de forma assíncrona."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "202", description = "Solicitação aceita, sem corpo de resposta",
            content = []
        ),
        ApiResponse(
            responseCode = "400", description = "Campos ou parâmetros inválidos",
            content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
        ),
        ApiResponse(
            responseCode = "401", description = "JWT ausente, inválido ou expirado",
            content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
        ),
        ApiResponse(
            responseCode = "503", description = "Persistência ou integração temporariamente indisponível",
            content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
        ),
        ApiResponse(
            responseCode = "403", description = "Usuário sem role ADMIN",
            content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
        ),
        ApiResponse(
            responseCode = "404", description = "Evento inexistente ou fora do estado FAILED",
            content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
        ),
    )
    fun retry(@Parameter(description = "UUIDv7 do evento da outbox; não é o ID do customer.", required = true) @PathVariable eventId: UUID): ResponseEntity<Void> {
        recover.retry(eventId)
        return ResponseEntity.accepted().build()
    }

    @PostMapping("/{eventId}/replay")
    @Operation(
        summary = "Republicar evento entregue",
        description = "Republica no Kafka um evento PUBLISHED ainda disponível na outbox e dentro da janela de validação de 30 dias. Requer ADMIN. Os consumidores processam a mensagem de forma assíncrona e tratam duplicatas."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "202", description = "Solicitação aceita, sem corpo de resposta",
            content = []
        ),
        ApiResponse(
            responseCode = "400", description = "Campos ou parâmetros inválidos",
            content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
        ),
        ApiResponse(
            responseCode = "401", description = "JWT ausente, inválido ou expirado",
            content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
        ),
        ApiResponse(
            responseCode = "503", description = "Persistência ou integração temporariamente indisponível",
            content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
        ),
        ApiResponse(
            responseCode = "403", description = "Usuário sem role ADMIN",
            content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
        ),
        ApiResponse(
            responseCode = "404", description = "Evento publicado não encontrado na outbox",
            content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
        ),
    )
    fun replay(@Parameter(description = "UUIDv7 do evento da outbox; não é o ID do customer.", required = true) @PathVariable eventId: UUID): ResponseEntity<Void> {
        recover.replay(eventId)
        return ResponseEntity.accepted().build()
    }
}
