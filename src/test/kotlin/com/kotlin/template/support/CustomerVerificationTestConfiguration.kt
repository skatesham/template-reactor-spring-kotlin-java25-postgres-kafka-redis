package com.kotlin.template.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar

@TestConfiguration(proxyBeanMethods = false)
class CustomerVerificationTestConfiguration {
    @Bean(destroyMethod = "close")
    fun customerVerificationHttpStub() = CustomerVerificationHttpStub()

    @Bean
    fun customerVerificationProperties(stub: CustomerVerificationHttpStub) = DynamicPropertyRegistrar { registry ->
        registry.add("app.customer.verification.url") { stub.url }
    }
}
