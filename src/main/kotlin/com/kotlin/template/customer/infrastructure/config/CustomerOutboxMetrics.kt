package com.kotlin.template.customer.infrastructure.config

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class CustomerOutboxMetrics(jdbc: JdbcTemplate, meters: MeterRegistry) {
    init {
        meters.gauge("customer.outbox.pending", jdbc) {
            it.queryForObject("SELECT count(*) FROM customer_outbox WHERE status='PENDING'", Long::class.java)
                ?.toDouble() ?: 0.0
        }
        meters.gauge("customer.outbox.failed", jdbc) {
            it.queryForObject("SELECT count(*) FROM customer_outbox WHERE status='FAILED'", Long::class.java)
                ?.toDouble() ?: 0.0
        }
        meters.gauge("customer.outbox.oldest.seconds", jdbc) {
            it.queryForObject(
                "SELECT coalesce(extract(epoch FROM (CURRENT_TIMESTAMP-min(occurred_at))),0) FROM customer_outbox WHERE status <> 'PUBLISHED'",
                Double::class.java
            ) ?: 0.0
        }
    }
}
