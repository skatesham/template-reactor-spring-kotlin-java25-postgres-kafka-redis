package com.kotlin.template.customer.application.usecase.find

import com.kotlin.template.customer.application.port.CustomerRepository
import com.kotlin.template.customer.application.result.CustomerDetails
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class ListCustomers(private val customers: CustomerRepository) {

    @Transactional(readOnly = true)
    fun execute(query: ListCustomersQuery): Flux<CustomerDetails> = Flux.defer {
        require(query.limit in 1..100)
        customers.list(query.ownerId, query.after, query.limit).map(CustomerDetails::from)
    }
}
