package com.kotlin.template.customer.application.usecase.update

import com.kotlin.template.customer.application.contract.CustomerChange
import com.kotlin.template.customer.application.exception.CustomerNotFound
import com.kotlin.template.customer.application.port.CustomerCache
import com.kotlin.template.customer.application.port.CustomerIds
import com.kotlin.template.customer.application.port.CustomerOutbox
import com.kotlin.template.customer.application.port.CustomerRepository
import com.kotlin.template.customer.application.result.CustomerDetails
import com.kotlin.template.customer.domain.model.CustomerId
import java.time.Clock
import java.time.temporal.ChronoUnit
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class UpdateCustomer(
    private val customers: CustomerRepository, private val outbox: CustomerOutbox,
    private val cache: CustomerCache, private val ids: CustomerIds, private val clock: Clock
) {

    @Transactional
    fun execute(command: UpdateCustomerCommand): Mono<CustomerDetails> = Mono.defer {
        customers.findForUpdate(CustomerId(command.id), command.ownerId).switchIfEmpty(Mono.error(CustomerNotFound()))
            .flatMap { customer ->
                val previousRevision = customer.revision
                customer.update(command.name, command.email, command.revision, ids.next(), clock.instant().truncatedTo(ChronoUnit.MICROS))
                customers.save(customer).thenMany(Flux.fromIterable(customer.events).concatMap { outbox.append(CustomerChange.from(it)) })
                    .then(cache.evictAfterCommit(command.id, previousRevision)).thenReturn(CustomerDetails.from(customer))
            }
    }
}
