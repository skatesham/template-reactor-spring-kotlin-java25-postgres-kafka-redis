package com.kotlin.template.customer.application.usecase.verify

import com.kotlin.template.customer.application.contract.CustomerChange
import com.kotlin.template.customer.application.exception.CustomerVerificationDenied
import com.kotlin.template.customer.application.port.CustomerVerification
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class VerifyCustomerChange(private val verification: CustomerVerification) {
    fun execute(change: CustomerChange): Mono<Void> = Mono.defer {
        change.validate()
        if (change.type == "customer.deleted.v1") {
            Mono.empty()
        } else {
            verification.verified().defaultIfEmpty(false).flatMap { verified ->
                if (verified) Mono.empty() else Mono.error(CustomerVerificationDenied())
            }
        }
    }
}
