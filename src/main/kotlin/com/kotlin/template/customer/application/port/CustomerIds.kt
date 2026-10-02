package com.kotlin.template.customer.application.port

import java.util.*

fun interface CustomerIds {
    fun next(): UUID
}
