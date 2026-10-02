package com.kotlin.template.audit.application.port

import com.kotlin.template.customer.application.contract.CustomerChange

interface CustomerAudit {
    fun record(change: CustomerChange): Boolean
    fun purge()
}
