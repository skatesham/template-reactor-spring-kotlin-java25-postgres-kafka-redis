package com.kotlin.template.identity.application.port

import reactor.core.publisher.Mono

interface PasswordHasher {

    fun hash(password: String): Mono<String>

    fun matches(password: String, hash: String): Mono<Boolean>
}
