package com.kotlin.template.customer.domain.model

import java.util.*

@JvmInline
value class CustomerEmail(val value: String) {
    init {
        require(
            value == value.trim()
                .lowercase(Locale.ROOT) && value.length <= 254 && value.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
        )
    }

    companion object {
        fun of(value: String) = CustomerEmail(value.trim().lowercase(Locale.ROOT))
    }
}
