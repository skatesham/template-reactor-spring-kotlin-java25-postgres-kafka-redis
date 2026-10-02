package com.kotlin.template.customer.infrastructure.messaging

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = ["app.customer.messaging.enabled"], havingValue = "true", matchIfMissing = true)
class CustomerKafkaConfiguration {
    @Bean
    fun customerChangesTopic() = TopicBuilder.name("customer.changes.v1")
        .partitions(3).replicas(1).config("retention.ms", "604800000")
        .config("retention.bytes", "104857600").build()
}
