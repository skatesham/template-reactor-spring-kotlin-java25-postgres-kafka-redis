package com.kotlin.template.notification.application.port

import com.kotlin.template.customer.application.contract.CustomerChange

interface CustomerNotifications {
    fun record(change: CustomerChange): Boolean
    fun purge()
}
