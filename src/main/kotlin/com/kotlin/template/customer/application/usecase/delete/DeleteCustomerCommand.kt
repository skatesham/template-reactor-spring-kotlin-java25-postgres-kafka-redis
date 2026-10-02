package com.kotlin.template.customer.application.usecase.delete

import java.util.*

data class DeleteCustomerCommand(val id: UUID, val ownerId: UUID, val revision: Long)
