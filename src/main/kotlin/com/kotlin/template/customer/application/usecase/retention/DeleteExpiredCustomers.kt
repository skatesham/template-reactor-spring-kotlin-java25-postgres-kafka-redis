package com.kotlin.template.customer.application.usecase.retention

import com.kotlin.template.customer.application.exception.CustomerNotFound
import com.kotlin.template.customer.application.port.CustomerRepository
import com.kotlin.template.customer.application.usecase.delete.DeleteCustomer
import com.kotlin.template.customer.application.usecase.delete.DeleteCustomerCommand
import com.kotlin.template.customer.domain.exception.CustomerRevisionConflict
import java.time.Clock
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class DeleteExpiredCustomers(
    private val customers: CustomerRepository,
    private val delete: DeleteCustomer,
    private val clock: Clock
) {

    fun execute(): Mono<Void> = Mono.defer {
        val before = clock.instant().minusSeconds(365 * 86400L)
        customers.expired(before, 100).concatMap { (id, owner) ->
            customers.find(id, owner).filter { it.updatedAt.isBefore(before) }.flatMap { customer ->
                delete.execute(DeleteCustomerCommand(id.value, owner, customer.revision))
                    .onErrorResume(CustomerNotFound::class.java) { Mono.empty() }
                    .onErrorResume(CustomerRevisionConflict::class.java) { Mono.empty() }
            }
        }.then()
    }
}
