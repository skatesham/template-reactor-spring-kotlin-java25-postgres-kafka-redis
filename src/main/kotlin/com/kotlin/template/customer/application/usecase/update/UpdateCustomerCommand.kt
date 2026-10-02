package com.kotlin.template.customer.application.usecase.update

import java.util.*

data class UpdateCustomerCommand(
    val id: UUID,
    val ownerId: UUID,
    val name: String,
    val email: String,
    val revision: Long
)
