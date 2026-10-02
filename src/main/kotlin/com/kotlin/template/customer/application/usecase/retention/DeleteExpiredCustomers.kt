package com.kotlin.template.customer.application.usecase.retention

import com.kotlin.template.customer.application.exception.CustomerNotFound
import com.kotlin.template.customer.application.usecase.delete.DeleteCustomer
import com.kotlin.template.customer.application.usecase.delete.DeleteCustomerCommand
import com.kotlin.template.customer.domain.exception.CustomerRevisionConflict
import com.kotlin.template.customer.domain.repository.CustomerRepository
import java.time.Clock
import org.springframework.stereotype.Service

@Service
class DeleteExpiredCustomers(
    private val customers: CustomerRepository,
    private val delete: DeleteCustomer,
    private val clock: Clock
) {
    fun execute() {
        val before = clock.instant().minusSeconds(365 * 86400L)
        customers.expired(before, 100).forEach { (id, owner) ->
            val customer = customers.find(id, owner)
            if (customer != null && customer.updatedAt.isBefore(before)) {
                try {
                    delete.execute(DeleteCustomerCommand(id.value, owner, customer.revision))
                } catch (_: CustomerNotFound) { /* Concurrent delete. */
                } catch (_: CustomerRevisionConflict) { /* Concurrent update: keep active customer. */
                }
            }
        }
    }
}
