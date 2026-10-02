package com.kotlin.template.customer.infrastructure.cache

import com.kotlin.template.customer.application.port.CustomerCache
import com.kotlin.template.customer.application.result.CustomerDetails
import io.micrometer.core.instrument.MeterRegistry
import java.time.Duration
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionSynchronization
import org.springframework.transaction.reactive.TransactionSynchronizationManager
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

@Component
class RedisCustomerCache(private val redis: ReactiveStringRedisTemplate, private val mapper: ObjectMapper,
    private val meters: MeterRegistry, @Value("\${app.customer.cache.ttl:PT1M}") private val ttl: Duration,
    @Value("\${spring.data.redis.timeout:500ms}") private val timeout: Duration) : CustomerCache {
    init {
        require(!ttl.isNegative && !ttl.isZero && ttl <= Duration.ofMinutes(5))
        require(!timeout.isNegative && !timeout.isZero)
    }

    private fun key(id: UUID, revision: Long) = "customer:v1:$id:$revision"

    override fun get(id: UUID, revision: Long): Mono<CustomerDetails> = safely("get") {
        redis.opsForValue().get(key(id, revision)).map { mapper.readValue(it, CustomerDetails::class.java) }
            .filter { it.id == id && it.revision == revision }
    }.doOnSuccess { meters.counter("customer.cache.requests", "result", if (it == null) "miss" else "hit").increment() }

    override fun put(details: CustomerDetails): Mono<Void> = safely("put") {
        redis.opsForValue().set(key(details.id, details.revision), mapper.writeValueAsString(details), ttl)
    }.then()

    override fun evictAfterCommit(id: UUID, revision: Long): Mono<Void> = TransactionSynchronizationManager.forCurrentTransaction()
        .doOnNext { manager ->
            check(manager.isSynchronizationActive)
            manager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit(): Mono<Void> = safely("evict") { redis.delete(key(id, revision)) }.then()
            })
        }.then()

    private fun <T : Any> safely(operation: String, action: () -> Mono<T>): Mono<T> = Mono.defer(action).timeout(timeout)
        .onErrorResume { meters.counter("customer.cache.failures", "operation", operation).increment(); Mono.empty() }
}
