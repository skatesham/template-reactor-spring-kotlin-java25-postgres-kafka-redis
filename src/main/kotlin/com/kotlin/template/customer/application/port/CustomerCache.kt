package com.kotlin.template.customer.application.port

import com.kotlin.template.customer.application.result.CustomerDetails
import java.util.*

interface CustomerCache {
    fun get(id: UUID, revision: Long): CustomerDetails?
    fun put(details: CustomerDetails)
    fun evictAfterCommit(id: UUID, revision: Long)
}
