package com.kotlin.template.identity.application.port

interface PasswordHasher {
    fun hash(password: String): String
    fun matches(password: String, hash: String): Boolean
}
