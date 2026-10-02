package com.kotlin.template.customer.application.usecase.find

import java.util.*

data class FindCustomerQuery(val id: UUID, val ownerId: UUID)
