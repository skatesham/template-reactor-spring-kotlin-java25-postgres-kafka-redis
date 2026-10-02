package com.kotlin.template.customer.application.usecase.find

import com.kotlin.template.customer.application.exception.CustomerNotFound
import com.kotlin.template.customer.application.port.CustomerCache
import com.kotlin.template.customer.application.port.CustomerRepository
import com.kotlin.template.customer.application.result.CustomerDetails
import com.kotlin.template.customer.domain.model.CustomerId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

@Service
class FindCustomer(private val customers: CustomerRepository, private val cache: CustomerCache) {
    // PostgreSQL checks ownership and revision even on a cache hit. This deliberately trades
    // one small indexed query for consistency and immediate revocation/deletion.

    @Transactional
    fun execute(query: FindCustomerQuery): Mono<CustomerDetails> = Mono.defer {
        customers.lockRevision(CustomerId(query.id), query.ownerId).switchIfEmpty(Mono.error(CustomerNotFound()))
            .flatMap { revision -> cache.get(query.id, revision).switchIfEmpty(Mono.defer {
                customers.find(CustomerId(query.id), query.ownerId).switchIfEmpty(Mono.error(CustomerNotFound()))
                    .map(CustomerDetails::from).flatMap { details -> cache.put(details).thenReturn(details) }
            }) }
    }
}
