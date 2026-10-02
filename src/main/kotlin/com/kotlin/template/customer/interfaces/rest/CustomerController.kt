package com.kotlin.template.customer.interfaces.rest

import com.kotlin.template.customer.application.usecase.create.CreateCustomer
import com.kotlin.template.customer.application.usecase.create.CreateCustomerCommand
import com.kotlin.template.customer.application.usecase.delete.DeleteCustomer
import com.kotlin.template.customer.application.usecase.delete.DeleteCustomerCommand
import com.kotlin.template.customer.application.usecase.find.FindCustomer
import com.kotlin.template.customer.application.usecase.find.FindCustomerQuery
import com.kotlin.template.customer.application.usecase.find.ListCustomers
import com.kotlin.template.customer.application.usecase.find.ListCustomersQuery
import com.kotlin.template.customer.application.usecase.update.UpdateCustomer
import com.kotlin.template.customer.application.usecase.update.UpdateCustomerCommand
import com.kotlin.template.customer.interfaces.rest.request.CreateCustomerRequest
import com.kotlin.template.customer.interfaces.rest.request.UpdateCustomerRequest
import com.kotlin.template.customer.interfaces.rest.response.CustomerResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.net.URI
import java.util.*
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers", description = "Cadastro e consulta de customers do usuário autenticado. Eventos são entregues de forma assíncrona.")
class CustomerController(
    private val create: CreateCustomer, private val find: FindCustomer,
    private val update: UpdateCustomer, private val delete: DeleteCustomer, private val list: ListCustomers
) {
    @PostMapping
    @Operation(
        summary = "Cadastrar customer",
        description = "Requer Idempotency-Key em formato UUID. Repetir a mesma chave e os mesmos dados normalizados retorna o customer existente. Dados e evento são gravados na mesma transação; a entrega aos consumidores é assíncrona."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201", description = "Customer criado ou criação anterior recuperada",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = CustomerResponse::class))], headers = [Header(name = "Location", description = "Rota para consultar o customer criado.", schema = Schema(type = "string"))]
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
            responseCode = "409", description = "Email já utilizado pelo proprietário ou chave de idempotência em conflito",
            content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
        ),
    )
    fun create(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID escolhido pelo cliente para identificar esta criação. Reutilize-o somente ao repetir a mesma operação.", required = true, schema = Schema(type = "string", format = "uuid"))
        @RequestHeader("Idempotency-Key") requestKey: UUID,
        @Valid @RequestBody request: CreateCustomerRequest
    ): ResponseEntity<CustomerResponse> {
        val result =
            create.execute(CreateCustomerCommand(UUID.fromString(jwt.subject), request.name, request.email, requestKey))
        return ResponseEntity.created(URI.create("/api/customers/${result.id}")).body(CustomerResponse.from(result))
    }

    @GetMapping
    @Operation(
        summary = "Listar meus customers",
        description = "Lista somente customers do usuário autenticado, em ordem crescente de ID. Para a próxima página, use o ID do último item em after. Retorna um array vazio quando não há resultados."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Página de customers",
            content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = CustomerResponse::class)))]
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
    )
    fun list(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "ID do último item da página anterior; omitido na primeira página.") @RequestParam(required = false) after: UUID?,
        @Parameter(description = "Quantidade máxima de itens por página, de 1 a 100.", schema = Schema(defaultValue = "20", minimum = "1", maximum = "100"))
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limit: Int
    ) =
        list.execute(ListCustomersQuery(UUID.fromString(jwt.subject), after, limit)).map(CustomerResponse::from)

    @GetMapping("/{id}")
    @Operation(
        summary = "Consultar customer",
        description = "Retorna somente um customer pertencente ao usuário autenticado. Recurso inexistente ou pertencente a outro usuário retorna 404."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Estado atual do customer",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = CustomerResponse::class))]
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
            responseCode = "404", description = "Customer não encontrado para este usuário",
            content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
        ),
    )
    fun find(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID do customer pertencente ao usuário autenticado.", required = true)
        @PathVariable id: UUID
    ) =
        CustomerResponse.from(find.execute(FindCustomerQuery(id, UUID.fromString(jwt.subject))))

    @PutMapping("/{id}")
    @Operation(
        summary = "Atualizar customer",
        description = "Substitui nome e email e incrementa a revisão. Envie a revisão atual para evitar sobrescrever uma alteração concorrente. A entrega do evento aos consumidores é assíncrona."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200", description = "Customer atualizado com a nova revisão",
            content = [Content(mediaType = "application/json", schema = Schema(implementation = CustomerResponse::class))]
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
            responseCode = "404", description = "Customer não encontrado para este usuário",
            content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
        ),
        ApiResponse(
            responseCode = "409", description = "Revisão desatualizada ou email já utilizado pelo proprietário",
            content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
        ),
    )
    fun update(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID do customer pertencente ao usuário autenticado.", required = true) @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateCustomerRequest
    ) =
        CustomerResponse.from(
            update.execute(
                UpdateCustomerCommand(
                    id,
                    UUID.fromString(jwt.subject),
                    request.name,
                    request.email,
                    request.revision
                )
            )
        )

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Excluir customer",
        description = "Exclui um customer do usuário autenticado usando a revisão atual. Uma alteração concorrente retorna 409. A entrega do evento de exclusão aos consumidores é assíncrona."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "204", description = "Customer excluído, sem corpo de resposta",
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
            responseCode = "404", description = "Customer não encontrado para este usuário",
            content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
        ),
        ApiResponse(
            responseCode = "409", description = "Revisão desatualizada",
            content = [Content(mediaType = "application/problem+json", schema = Schema(implementation = ProblemDetail::class))]
        ),
    )
    fun delete(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "UUID do customer pertencente ao usuário autenticado.", required = true) @PathVariable id: UUID,
        @Parameter(description = "Revisão atual do customer obtida na consulta.", required = true, schema = Schema(minimum = "1"))
        @RequestParam @Positive revision: Long
    ): ResponseEntity<Void> {
        delete.execute(DeleteCustomerCommand(id, UUID.fromString(jwt.subject), revision))
        return ResponseEntity.noContent().build()
    }
}
