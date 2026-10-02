package com.kotlin.template.notification.application.usecase.retention

import com.kotlin.template.notification.application.port.CustomerNotifications
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

@Service
class PurgeCustomerNotifications(private val records: CustomerNotifications) {

    @Transactional
    fun execute(): Mono<Void> = Mono.defer { records.purge() }
}
