package com.kotlin.template.notification.infrastructure.messaging

import com.kotlin.template.shared.infrastructure.messaging.KafkaListenerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.ConsumerFactory

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = ["app.customer.messaging.enabled"], havingValue = "true", matchIfMissing = true)
class NotificationKafkaConfiguration {

    @Bean
    fun notificationDeadLetterTopic() = TopicBuilder.name("customer.changes.v1.notification.DLT")
        .partitions(3).replicas(1).config("retention.ms", "604800000")
        .config("retention.bytes", "104857600").build()

    @Bean
    fun notificationKafkaFactory(factory: KafkaListenerFactory, consumerFactory: ConsumerFactory<String, String>,
        @Value("\${app.customer.consumer.backoff-ms:1000}") backoff: Long) = factory.create(consumerFactory, backoff)
}
