package com.kotlin.template.customer.application.port

import com.kotlin.template.customer.application.contract.CustomerChange

fun interface CustomerEventPublisher {
    fun publish(change: CustomerChange)
}
