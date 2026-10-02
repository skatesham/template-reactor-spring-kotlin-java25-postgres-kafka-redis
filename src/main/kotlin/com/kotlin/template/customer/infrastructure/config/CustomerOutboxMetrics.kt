package com.kotlin.template.customer.infrastructure.config

import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.atomic.AtomicReference
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class CustomerOutboxMetrics(private val db: DatabaseClient, meters: MeterRegistry) {
    private val pending = AtomicReference(0.0)
    private val failed = AtomicReference(0.0)
    private val oldest = AtomicReference(0.0)
    init {
        meters.gauge("customer.outbox.pending", pending) { it.get() }
        meters.gauge("customer.outbox.failed", failed) { it.get() }
        meters.gauge("customer.outbox.oldest.seconds", oldest) { it.get() }
    }
    // Scrapes read memory; they never wait for SQL on a metrics/HTTP thread.

    @Scheduled(fixedDelayString = "\${app.customer.outbox.metrics-poll-ms:5000}")
    fun refresh(): Mono<Void> = Mono.defer {
        db.sql("""SELECT count(*) FILTER (WHERE status='PENDING') AS pending,
            count(*) FILTER (WHERE status='FAILED') AS failed,
            coalesce(extract(epoch FROM (CURRENT_TIMESTAMP-min(occurred_at) FILTER (WHERE status <> 'PUBLISHED'))),0) AS oldest
            FROM customer_outbox""").map { row, _ ->
                Triple((row.get("pending") as Number).toDouble(), (row.get("failed") as Number).toDouble(),
                    (row.get("oldest") as Number).toDouble())
            }.one().doOnNext { (p, f, o) -> pending.set(p); failed.set(f); oldest.set(o) }.then()
    }
}
