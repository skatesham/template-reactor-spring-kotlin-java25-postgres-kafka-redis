package com.kotlin.template.customer.application.port

import reactor.core.publisher.Mono

fun interface CustomerVerification {
    fun verified(): Mono<Boolean>
}
