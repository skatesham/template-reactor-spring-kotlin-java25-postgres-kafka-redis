package com.kotlin.template.customer.application.port

import com.kotlin.template.customer.application.contract.CustomerChange
import reactor.core.publisher.Mono

fun interface CustomerEventPublisher {

    fun publish(change: CustomerChange): Mono<Void>
}
