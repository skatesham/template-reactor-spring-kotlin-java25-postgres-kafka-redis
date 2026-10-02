package com.kotlin.template.notification.interfaces.rest.response

import com.kotlin.template.notification.application.result.NotificationDetails
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "Registro de uma alteração de customer, sem nome ou email do perfil.")
data class NotificationResponse(
    @field:Schema(description = "UUIDv7 do evento que originou a notificação.", format = "uuid")
    val eventId: UUID,
    @field:Schema(description = "UUIDv7 do customer alterado.", format = "uuid")
    val customerId: UUID,
    @field:Schema(description = "Revisão do customer no momento do evento.", minimum = "1", example = "1")
    val revision: Long,
    @field:Schema(description = "Tipo versionado da alteração.", allowableValues = ["customer.created.v1", "customer.updated.v1", "customer.deleted.v1"])
    val type: String,
    @field:Schema(description = "Instante da alteração em UTC; não é o instante de entrega.", format = "date-time")
    val occurredAt: Instant,
) {
    companion object {
        fun from(details: NotificationDetails) = NotificationResponse(
            details.eventId, details.customerId, details.revision, details.type, details.occurredAt
        )
    }
}
