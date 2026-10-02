package com.kotlin.template.customer.application.usecase.create

import java.util.*

data class CreateCustomerCommand(val ownerId: UUID, val name: String, val email: String, val requestKey: UUID)
