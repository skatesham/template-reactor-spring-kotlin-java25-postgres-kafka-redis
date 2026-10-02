package com.kotlin.template.audit.application.port

import com.kotlin.template.customer.application.contract.CustomerChange
import reactor.core.publisher.Mono

interface CustomerAudit {

    fun record(change: CustomerChange): Mono<Boolean>

    fun purge(): Mono<Void>
}
