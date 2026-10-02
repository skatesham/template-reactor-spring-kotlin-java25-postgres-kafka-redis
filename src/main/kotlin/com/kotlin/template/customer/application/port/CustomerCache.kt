package com.kotlin.template.customer.application.port

import com.kotlin.template.customer.application.result.CustomerDetails
import java.util.*
import reactor.core.publisher.Mono

interface CustomerCache {

    fun get(id: UUID, revision: Long): Mono<CustomerDetails>

    fun put(details: CustomerDetails): Mono<Void>

    fun evictAfterCommit(id: UUID, revision: Long): Mono<Void>
}
