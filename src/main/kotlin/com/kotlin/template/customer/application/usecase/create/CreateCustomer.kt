package com.kotlin.template.customer.application.usecase.create

import com.kotlin.template.customer.application.contract.CustomerChange
import com.kotlin.template.customer.application.exception.CustomerCreationConflict
import com.kotlin.template.customer.application.port.CustomerCreationRequests
import com.kotlin.template.customer.application.port.CustomerIds
import com.kotlin.template.customer.application.port.CustomerOutbox
import com.kotlin.template.customer.application.port.CustomerRepository
import com.kotlin.template.customer.application.result.CustomerDetails
import com.kotlin.template.customer.domain.model.Customer
import com.kotlin.template.customer.domain.model.CustomerEmail
import com.kotlin.template.customer.domain.model.CustomerId
import java.time.Clock
import java.time.temporal.ChronoUnit
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class CreateCustomer(
    private val customers: CustomerRepository, private val outbox: CustomerOutbox,
    private val ids: CustomerIds, private val clock: Clock, private val requests: CustomerCreationRequests
) {

    @Transactional
    fun execute(command: CreateCustomerCommand): Mono<CustomerDetails> = Mono.defer {
        requests.reserve(command.ownerId, command.requestKey, command.name.trim(), CustomerEmail.of(command.email).value)
            .flatMap { previous -> customers.find(CustomerId(previous), command.ownerId)
                .switchIfEmpty(Mono.error(CustomerCreationConflict())).map(CustomerDetails::from) }
            .switchIfEmpty(Mono.defer {
                val customer = Customer.register(CustomerId(ids.next()), command.ownerId, command.name,
                    command.email, ids.next(), clock.instant().truncatedTo(ChronoUnit.MICROS))
                customers.create(customer).thenMany(Flux.fromIterable(customer.events)
                    .concatMap { outbox.append(CustomerChange.from(it)) })
                    .then(requests.complete(command.ownerId, command.requestKey, customer.id.value))
                    .thenReturn(CustomerDetails.from(customer))
            })
    }
}
