package com.kotlin.template.notification.interfaces.rest

import com.kotlin.template.notification.application.usecase.find.FindNotifications
import com.kotlin.template.notification.interfaces.rest.response.NotificationResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ProblemDetail
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@Tag(name = "Notificações", description = "Inbox de alterações de customers do usuário autenticado.")
class NotificationController(private val find: FindNotifications) {
    @GetMapping("/api/notifications")
    @Operation(
        summary = "Consultar minhas notificações",
        description = "Retorna até 100 notificações, das mais recentemente registradas para as mais antigas. " +
            "A entrega é assíncrona: uma alteração de customer pode demorar a aparecer. " +
            "Retorna um array vazio quando não há notificações."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Notificações do usuário autenticado",
            content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = NotificationResponse::class)))]
        ),
        ApiResponse(
            responseCode = "401", description = "JWT ausente, inválido ou expirado",
            content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
        ),
        ApiResponse(
            responseCode = "503", description = "Persistência temporariamente indisponível",
            content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
        ),
    )
    fun find(@Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt) =
        find.execute(UUID.fromString(jwt.subject)).map(NotificationResponse::from)
}
