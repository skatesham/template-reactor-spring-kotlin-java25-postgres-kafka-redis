package com.kotlin.template.customer.application.usecase.delete

import com.kotlin.template.customer.application.contract.CustomerChange
import com.kotlin.template.customer.application.exception.CustomerNotFound
import com.kotlin.template.customer.application.port.CustomerCache
import com.kotlin.template.customer.application.port.CustomerIds
import com.kotlin.template.customer.application.port.CustomerOutbox
import com.kotlin.template.customer.application.port.CustomerRepository
import com.kotlin.template.customer.domain.model.CustomerId
import java.time.Clock
import java.time.temporal.ChronoUnit
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class DeleteCustomer(
    private val customers: CustomerRepository, private val outbox: CustomerOutbox,
    private val cache: CustomerCache, private val ids: CustomerIds, private val clock: Clock
) {

    @Transactional
    fun execute(command: DeleteCustomerCommand): Mono<Void> = Mono.defer {
        customers.findForUpdate(CustomerId(command.id), command.ownerId).switchIfEmpty(Mono.error(CustomerNotFound()))
            .flatMap { customer ->
                val previousRevision = customer.revision
                customer.delete(command.revision, ids.next(), clock.instant().truncatedTo(ChronoUnit.MICROS))
                customers.delete(customer).thenMany(Flux.fromIterable(customer.events).concatMap { outbox.append(CustomerChange.from(it)) })
                    .then(cache.evictAfterCommit(command.id, previousRevision))
            }
    }
}
