package com.kotlin.template.customer.application.port

import com.kotlin.template.customer.application.contract.CustomerChange

data class PendingCustomerChange(val change: CustomerChange, val attempts: Int)
