package com.kotlin.template.customer.application.result

import com.kotlin.template.customer.domain.model.Customer
import java.time.Instant
import java.util.*

data class CustomerDetails(
    val id: UUID, val name: String, val email: String, val revision: Long,
    val createdAt: Instant, val updatedAt: Instant,
) {
    companion object {
        fun from(customer: Customer) = CustomerDetails(
            customer.id.value, customer.name,
            customer.email.value, customer.revision, customer.createdAt, customer.updatedAt
        )
    }
}
