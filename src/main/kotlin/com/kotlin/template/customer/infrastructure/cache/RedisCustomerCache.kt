package com.kotlin.template.customer.infrastructure.cache

import com.kotlin.template.customer.application.port.CustomerCache
import com.kotlin.template.customer.application.result.CustomerDetails
import io.micrometer.core.instrument.MeterRegistry
import java.time.Duration
import java.util.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import tools.jackson.databind.ObjectMapper

@Component
class RedisCustomerCache(
    private val redis: StringRedisTemplate, private val mapper: ObjectMapper,
    private val meters: MeterRegistry, @Value("\${app.customer.cache.ttl:PT1M}") private val ttl: Duration,
) : CustomerCache {
    init {
        require(!ttl.isNegative && !ttl.isZero && ttl <= Duration.ofMinutes(5))
    }

    private fun key(id: UUID, revision: Long) = "customer:v1:$id:$revision"
    override fun get(id: UUID, revision: Long): CustomerDetails? {
        val details = safely("get") {
            redis.opsForValue().get(key(id, revision))?.let { mapper.readValue(it, CustomerDetails::class.java) }
        }?.takeIf { it.id == id && it.revision == revision }
        meters.counter("customer.cache.requests", "result", if (details == null) "miss" else "hit").increment()
        return details
    }

    override fun put(details: CustomerDetails) {
        safely("put") {
            redis.opsForValue().set(key(details.id, details.revision), mapper.writeValueAsString(details), ttl)
        }
    }

    override fun evictAfterCommit(id: UUID, revision: Long) {
        check(TransactionSynchronizationManager.isSynchronizationActive())
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                safely("evict") { redis.delete(key(id, revision)) }
            }
        })
    }

    private fun <T> safely(operation: String, action: () -> T): T? = try {
        action()
    } catch (_: Exception) {
        meters.counter("customer.cache.failures", "operation", operation).increment(); null
    }
}
