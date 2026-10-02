package com.kotlin.template.customer

import com.kotlin.template.TestcontainersConfiguration
import com.kotlin.template.customer.application.result.CustomerDetails
import com.kotlin.template.customer.application.usecase.create.CreateCustomer
import com.kotlin.template.customer.application.usecase.create.CreateCustomerCommand
import com.kotlin.template.customer.application.usecase.update.UpdateCustomer
import com.kotlin.template.customer.application.usecase.update.UpdateCustomerCommand
import com.kotlin.template.support.CustomerVerificationHttpStub
import com.kotlin.template.support.ReactiveTestConfiguration
import com.kotlin.template.support.TestDatabase
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.testcontainers.kafka.KafkaContainer

@Import(ReactiveTestConfiguration::class, TestcontainersConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = [
    "app.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.r2dbc.password=test",
    "spring.docker.compose.enabled=false",
    "app.customer.outbox.poll-ms=20",
    "app.customer.consumer.backoff-ms=10",
])
class CustomerVerificationIntegrationTests {
    @Autowired lateinit var create: CreateCustomer
    @Autowired lateinit var update: UpdateCustomer
    @Autowired lateinit var db: TestDatabase
    @Autowired lateinit var provider: CustomerVerificationHttpStub
    @Autowired lateinit var kafka: KafkaContainer

    @AfterEach
    fun resetProvider() {
        provider.status = "VERIFIED"
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `VERIFIED lets the Kafka consumer persist notifications for creation and email update`(emailUpdate: Boolean) {
        val before = provider.requests.get()
        val customer = mutation(emailUpdate, "VERIFIED")
        await { count("customer_notifications", customer.id) == customer.revision.toInt() }
        await { count("customer_audit", customer.id) == customer.revision.toInt() }
        assertEquals(customer.revision, cursor(customer.id))
        assertTrue(provider.requests.get() > before)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `DENIED retries then sends to notification DLT without rolling back the customer or audit`(emailUpdate: Boolean) {
        val before = provider.requests.get()
        val customer = mutation(emailUpdate, "DENIED")
        val eventId = db.queryForObject(
            "SELECT event_id FROM customer_outbox WHERE customer_id=? AND revision=?",
            UUID::class.java, customer.id, customer.revision
        )!!
        val failure = awaitDeadLetter(customer.id)
        assertEquals(eventId.toString(), failure.headers().lastHeader("event-id").value().toString(Charsets.UTF_8))
        assertEquals("{\"status\":\"failed\"}", failure.value())
        assertEquals(customer.revision.toInt() - 1, count("customer_notifications", customer.id))
        assertEquals(customer.revision - 1, cursor(customer.id))
        await { count("customer_audit", customer.id) == customer.revision.toInt() }
        assertEquals(customer.email, db.queryForObject("SELECT email FROM customers WHERE id=?", String::class.java, customer.id))
        assertEquals(if (emailUpdate) 5 else 4, provider.requests.get() - before)
    }

    private fun mutation(emailUpdate: Boolean, status: String): CustomerDetails {
        val owner = UUID.randomUUID()
        provider.status = if (emailUpdate) "VERIFIED" else status
        val customer = create.execute(CreateCustomerCommand(owner, "Synthetic Customer",
            "verification-$owner@example.com", UUID.randomUUID())).block()!!
        if (!emailUpdate) return customer
        await { count("customer_notifications", customer.id) == 1 }
        provider.status = status
        return update.execute(UpdateCustomerCommand(customer.id, owner, "Synthetic Customer",
            "updated-$owner@example.com", 1)).block()!!
    }

    private fun count(table: String, id: UUID) =
        db.queryForObject("SELECT count(*) FROM $table WHERE customer_id=?", Int::class.java, id)!!

    private fun cursor(id: UUID) = db.queryForObject(
        "SELECT revision FROM customer_notifications_cursor WHERE customer_id=?", Long::class.java, id
    ) ?: 0L

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(25)
        }
        assertTrue(condition(), "Consumer did not finish within 30 seconds")
    }

    private fun awaitDeadLetter(id: UUID): ConsumerRecord<String, String> {
        val topic = "customer.changes.v1.notification.DLT"
        KafkaConsumer<String, String>(mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
        )).use { consumer ->
            val partitions = (0..2).map { TopicPartition(topic, it) }
            consumer.assign(partitions)
            consumer.seekToBeginning(partitions)
            val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
            while (System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(100)).firstOrNull { it.key() == id.toString() }?.let { return it }
            }
        }
        error("Denied verification did not reach notification DLT")
    }
}
