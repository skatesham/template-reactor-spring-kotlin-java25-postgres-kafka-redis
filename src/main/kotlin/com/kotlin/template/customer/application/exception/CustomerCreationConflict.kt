package com.kotlin.template.customer.application.exception

class CustomerCreationConflict : RuntimeException("Idempotency key already used")
