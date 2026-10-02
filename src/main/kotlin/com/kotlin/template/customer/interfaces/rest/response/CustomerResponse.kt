package com.kotlin.template.customer.interfaces.rest.response

import com.kotlin.template.customer.application.result.CustomerDetails
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.*
import java.time.Instant
import java.util.*
import java.util.UUID

@Schema(description = "Estado do customer. Não inclui o proprietário nem detalhes internos de persistência.")
data class CustomerResponse(
    @field:Schema(
        description = "Identificador técnico UUIDv7 do customer.", format = "uuid", example = "0199b91d-5000-7000-8000-000000000001", requiredMode = Schema.RequiredMode.REQUIRED
    )
    val id: UUID,
    @field:Schema(
        description = "Nome normalizado.", example = "Cliente Exemplo", requiredMode = Schema.RequiredMode.REQUIRED
    )
    val name: String,
    @field:Schema(
        description = "Email normalizado.", example = "cliente@example.com", requiredMode = Schema.RequiredMode.REQUIRED
    )
    val email: String,
    @field:Schema(
        description = "Revisão atual. Começa em 1 e aumenta a cada alteração; deve ser enviada ao atualizar ou excluir.", example = "1", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED
    )
    val revision: Long,
    @field:Schema(
        description = "Instante de criação em UTC.", format = "date-time", example = "2026-10-01T12:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED
    )
    val createdAt: Instant,
    @field:Schema(
        description = "Instante da última alteração em UTC.", format = "date-time", example = "2026-10-01T12:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED
    )
    val updatedAt: Instant,
) {
    companion object {
        fun from(details: CustomerDetails) = CustomerResponse(
            details.id, details.name, details.email, details.revision, details.createdAt, details.updatedAt
        )
    }
}
