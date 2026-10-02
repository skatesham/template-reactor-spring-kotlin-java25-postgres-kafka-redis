package com.kotlin.template.customer.application.usecase.update

import com.kotlin.template.customer.application.contract.CustomerChange
import com.kotlin.template.customer.application.exception.CustomerNotFound
import com.kotlin.template.customer.application.port.CustomerCache
import com.kotlin.template.customer.application.port.CustomerIds
import com.kotlin.template.customer.application.port.CustomerOutbox
import com.kotlin.template.customer.application.result.CustomerDetails
import com.kotlin.template.customer.domain.model.CustomerId
import com.kotlin.template.customer.domain.repository.CustomerRepository
import java.time.Clock
import java.time.temporal.ChronoUnit
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateCustomer(
    private val customers: CustomerRepository, private val outbox: CustomerOutbox,
    private val cache: CustomerCache, private val ids: CustomerIds, private val clock: Clock
) {
    @Transactional
    fun execute(command: UpdateCustomerCommand): CustomerDetails {
        val customer = customers.findForUpdate(CustomerId(command.id), command.ownerId) ?: throw CustomerNotFound()
        val previousRevision = customer.revision
        customer.update(
            command.name,
            command.email,
            command.revision,
            ids.next(),
            clock.instant().truncatedTo(ChronoUnit.MICROS)
        )
        customers.save(customer)
        customer.events.forEach { outbox.append(CustomerChange.from(it)) }
        cache.evictAfterCommit(command.id, previousRevision)
        return CustomerDetails.from(customer)
    }
}
