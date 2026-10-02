package com.kotlin.template.customer.infrastructure.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.kotlin.template.customer.application.exception.CustomerVerificationUnavailable
import com.kotlin.template.customer.application.port.CustomerVerification
import java.net.URI
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Component
class WebClientCustomerVerification(
    builder: WebClient.Builder,
    @Value("\${app.customer.verification.url}") private val url: URI,
    @Value("\${app.customer.verification.timeout:PT3S}") private val timeout: Duration,
) : CustomerVerification {
    private val client = builder.build()

    init {
        require(url.scheme in setOf("http", "https") && !url.host.isNullOrBlank())
        require(!timeout.isNegative && !timeout.isZero)
    }

    override fun verified(): Mono<Boolean> = Mono.defer {
        client.get().uri(url).accept(MediaType.APPLICATION_JSON).retrieve()
            .bodyToMono(VerificationResponse::class.java)
            .map { it.status == "VERIFIED" }
            .defaultIfEmpty(false)
    }.timeout(timeout).onErrorMap { CustomerVerificationUnavailable() }

    // The provider's remaining fields are intentionally outside our application contract.
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class VerificationResponse(val status: String? = null)
}
