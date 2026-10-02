package com.kotlin.template.customer.application.usecase.find

import com.kotlin.template.customer.application.result.CustomerDetails
import com.kotlin.template.customer.domain.repository.CustomerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListCustomers(private val customers: CustomerRepository) {
    @Transactional(readOnly = true)
    fun execute(query: ListCustomersQuery): List<CustomerDetails> {
        require(query.limit in 1..100)
        return customers.list(query.ownerId, query.after, query.limit).map(CustomerDetails::from)
    }
}
