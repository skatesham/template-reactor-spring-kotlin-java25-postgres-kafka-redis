package com.kotlin.template.identity.infrastructure.security

import com.kotlin.template.identity.application.port.PasswordHasher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Component
class SpringPasswordHasher(private val encoder: PasswordEncoder) : PasswordHasher {
    // Password derivation is CPU intensive and must never run on a Netty event loop.

    override fun hash(password: String): Mono<String> = Mono.fromCallable {
        checkNotNull(encoder.encode(password)) { "Password encoder returned no hash" }
    }.subscribeOn(Schedulers.boundedElastic())

    override fun matches(password: String, hash: String): Mono<Boolean> = Mono.fromCallable {
        encoder.matches(password, hash)
    }.subscribeOn(Schedulers.boundedElastic())
}
