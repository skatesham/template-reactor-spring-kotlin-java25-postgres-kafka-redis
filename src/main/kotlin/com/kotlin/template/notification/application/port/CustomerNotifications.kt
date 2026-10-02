package com.kotlin.template.notification.application.port

import com.kotlin.template.customer.application.contract.CustomerChange
import reactor.core.publisher.Mono

interface CustomerNotifications {

    fun record(change: CustomerChange): Mono<Boolean>

    fun purge(): Mono<Void>
}
