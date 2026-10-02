package com.kotlin.template.customer.application.usecase.create

import com.kotlin.template.customer.application.contract.CustomerChange
import com.kotlin.template.customer.application.exception.CustomerCreationConflict
import com.kotlin.template.customer.application.port.CustomerCreationRequests
import com.kotlin.template.customer.application.port.CustomerIds
import com.kotlin.template.customer.application.port.CustomerOutbox
import com.kotlin.template.customer.application.result.CustomerDetails
import com.kotlin.template.customer.domain.model.Customer
import com.kotlin.template.customer.domain.model.CustomerEmail
import com.kotlin.template.customer.domain.model.CustomerId
import com.kotlin.template.customer.domain.repository.CustomerRepository
import java.time.Clock
import java.time.temporal.ChronoUnit
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateCustomer(
    private val customers: CustomerRepository, private val outbox: CustomerOutbox,
    private val ids: CustomerIds, private val clock: Clock, private val requests: CustomerCreationRequests
) {
    @Transactional
    fun execute(command: CreateCustomerCommand): CustomerDetails {
        val previousId = requests.reserve(
            command.ownerId,
            command.requestKey,
            command.name.trim(),
            CustomerEmail.of(command.email).value
        )
        if (previousId != null) {
            val previous = customers.find(CustomerId(previousId), command.ownerId) ?: throw CustomerCreationConflict()
            return CustomerDetails.from(previous)
        }
        val customer = Customer.register(
            CustomerId(ids.next()), command.ownerId, command.name,
            command.email, ids.next(), clock.instant().truncatedTo(ChronoUnit.MICROS)
        )
        customers.create(customer)
        customer.events.forEach { outbox.append(CustomerChange.from(it)) }
        requests.complete(command.ownerId, command.requestKey, customer.id.value)
        return CustomerDetails.from(customer)
    }
}
