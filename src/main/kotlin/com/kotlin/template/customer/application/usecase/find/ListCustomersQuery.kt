package com.kotlin.template.customer.application.usecase.find

import java.util.*

data class ListCustomersQuery(val ownerId: UUID, val after: UUID?, val limit: Int)
