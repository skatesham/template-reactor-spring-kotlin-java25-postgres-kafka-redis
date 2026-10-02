package com.kotlin.template.customer

import com.fasterxml.uuid.Generators
import com.kotlin.template.customer.application.contract.CustomerChange
import com.kotlin.template.customer.application.exception.CustomerVerificationDenied
import com.kotlin.template.customer.application.port.CustomerVerification
import com.kotlin.template.customer.application.usecase.verify.VerifyCustomerChange
import com.kotlin.template.customer.infrastructure.client.WebClientCustomerVerification
import com.kotlin.template.support.CustomerVerificationHttpStub
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.web.reactive.function.client.WebClient
import reactor.test.StepVerifier

class CustomerVerificationClientTests {
    @ParameterizedTest
    @ValueSource(strings = ["customer.created.v1", "customer.updated.v1"])
    fun `VERIFIED accepts the event regardless of email and risk fields`(type: String) {
        CustomerVerificationHttpStub().use { provider ->
            StepVerifier.create(verifier(provider).execute(change(type)))
                .verifyComplete()
            assertEquals(1, provider.requests.get())
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["customer.created.v1", "customer.updated.v1"])
    fun `DENIED rejects the event`(type: String) {
        CustomerVerificationHttpStub().use { provider ->
            provider.status = "DENIED"
            StepVerifier.create(verifier(provider).execute(change(type)))
                .expectError(CustomerVerificationDenied::class.java).verify()
            assertEquals(1, provider.requests.get())
        }
    }

    @Test
    fun `deletion does not depend on the verification provider`() {
        val verifier = VerifyCustomerChange(CustomerVerification { error("Provider must not be called for deletion") })
        StepVerifier.create(verifier.execute(change("customer.deleted.v1"))).verifyComplete()
    }

    private fun verifier(provider: CustomerVerificationHttpStub) = VerifyCustomerChange(
        WebClientCustomerVerification(WebClient.builder(), URI(provider.url), Duration.ofSeconds(3))
    )

    private fun change(type: String): CustomerChange {
        val ids = Generators.timeBasedEpochGenerator()
        return CustomerChange(ids.generate(), ids.generate(), UUID.randomUUID(),
            if (type == "customer.created.v1") 1 else 2, type, Instant.now())
    }
}
