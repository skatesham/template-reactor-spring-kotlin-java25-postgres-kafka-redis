package com.kotlin.template.customer

import com.kotlin.template.audit.application.port.CustomerAudit
import com.kotlin.template.audit.application.usecase.record.RecordCustomerAudit
import com.kotlin.template.audit.application.usecase.retention.PurgeCustomerAudit
import com.kotlin.template.audit.infrastructure.persistence.adapter.JdbcCustomerAudit
import com.kotlin.template.customer.application.contract.CustomerChange
import com.kotlin.template.customer.application.usecase.create.CreateCustomer
import com.kotlin.template.customer.application.usecase.create.CreateCustomerCommand
import com.kotlin.template.customer.application.usecase.delivery.PublishCustomerOutbox
import com.kotlin.template.customer.application.usecase.retention.DeleteExpiredCustomers
import com.kotlin.template.customer.application.usecase.update.UpdateCustomer
import com.kotlin.template.customer.application.usecase.update.UpdateCustomerCommand
import com.kotlin.template.notification.application.usecase.record.NotifyCustomerChange
import com.kotlin.template.notification.application.usecase.retention.PurgeCustomerNotifications
import io.micrometer.core.instrument.MeterRegistry
import java.time.Duration
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.ObjectMapper

@Testcontainers
@ExtendWith(OutputCaptureExtension::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@Import(CustomerFlowIntegrationTests.FailureConfiguration::class)
@SpringBootTest(
    properties = [
        "app.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "spring.datasource.password=test",
        "spring.docker.compose.enabled=false",
        "app.customer.jobs.enabled=false",
        "app.customer.cache.ttl=PT2S",
        "app.customer.consumer.backoff-ms=50",
        "app.customer.outbox.max-attempts=2",
    ]
)
class CustomerFlowIntegrationTests {
    @Autowired
    lateinit var mvc: MockMvc
    @Autowired
    lateinit var jdbc: JdbcTemplate
    @Autowired
    lateinit var mapper: ObjectMapper
    @Autowired
    lateinit var redisTemplate: StringRedisTemplate
    @Autowired
    lateinit var publish: PublishCustomerOutbox
    @Autowired
    lateinit var create: CreateCustomer
    @Autowired
    lateinit var update: UpdateCustomer
    @Autowired
    lateinit var txManager: PlatformTransactionManager
    @Autowired
    lateinit var audit: RecordCustomerAudit
    @Autowired
    lateinit var notifications: NotifyCustomerChange
    @Autowired
    lateinit var purgeAudit: PurgeCustomerAudit
    @Autowired
    lateinit var purgeNotifications: PurgeCustomerNotifications
    @Autowired
    lateinit var failingAudit: FailingAudit
    @Autowired
    lateinit var expired: DeleteExpiredCustomers
    @Autowired
    lateinit var meters: MeterRegistry

    @AfterEach
    fun resetFailures() {
        failingAudit.failures.clear()
    }

    @Test
    fun `authenticated REST to outbox Kafka independent audit and notification includes cache and hard deletion`() {
        val owner = UUID.randomUUID()
        val token = signupAndLogin(owner)
        val response = mvc.perform(
            post("/api/customers").header("Authorization", "Bearer $token")
                .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Synthetic Customer","email":"customer-$owner@example.com"}""")
        )
            .andExpect(status().isCreated).andExpect(header().exists("Location")).andReturn().response.contentAsString
        val id = UUID.fromString(mapper.readTree(response)["id"].asString())
        val realOwner = jdbc.queryForObject("SELECT owner_id FROM customers WHERE id=?", UUID::class.java, id)!!
        assertEquals(7, id.version())
        val first = event(id, 1)
        assertEquals(7, first.eventId.version())
        val payload = mapper.writeValueAsString(first)
        assertFalse(payload.contains("email")); assertFalse(payload.contains("name")); assertFalse(payload.contains("example.com"))
        assertEquals("PENDING", statusOf(first.eventId))
        mvc.perform(get("/api/customers/$id").header("Authorization", "Bearer $token")).andExpect(status().isOk)
        assertNotNull(redisTemplate.opsForValue().get(key(id, 1)))
        mvc.perform(get("/api/customers/$id").header("Authorization", "Bearer $token")).andExpect(status().isOk)
        assertTrue(meters.get("customer.cache.requests").tag("result", "hit").counter().count() >= 1)
        assertTrue(redisTemplate.getExpire(key(id, 1)) in 0..2)
        mvc.perform(
            put("/api/customers/$id").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Changed Customer","email":"changed-$owner@example.com","revision":1}""")
        )
            .andExpect(status().isOk).andExpect(jsonPath("$.revision").value(2))
        assertNull(redisTemplate.opsForValue().get(key(id, 1)))
        mvc.perform(get("/api/customers/$id").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk).andExpect(jsonPath("$.name").value("Changed Customer"))
        assertNotNull(redisTemplate.opsForValue().get(key(id, 2)))
        mvc.perform(delete("/api/customers/$id?revision=2").header("Authorization", "Bearer $token"))
            .andExpect(status().isNoContent)
        assertNull(redisTemplate.opsForValue().get(key(id, 2)))
        assertEquals(0, count("customers", id, "id"))
        mvc.perform(get("/api/customers/$id").header("Authorization", "Bearer $token")).andExpect(status().isNotFound)
        drain()
        await { count("customer_audit", id) == 3 && count("customer_notifications", id) == 3 }
        for (table in listOf("customer_audit", "customer_notifications")) {
            assertEquals(
                listOf(1L, 2L, 3L),
                jdbc.queryForList(
                    "SELECT revision FROM $table WHERE customer_id=? ORDER BY recorded_at",
                    Long::class.java,
                    id
                )
            )
        }
        mvc.perform(get("/api/notifications").with(user(realOwner))).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].type").value("customer.deleted.v1"))
        audit.execute(first); notifications.execute(first)
        assertEquals(3, count("customer_audit", id)); assertEquals(3, count("customer_notifications", id))
    }

    @Test
    fun `authorization validation pagination duplicate email and stale mutations do not leak or create events`(output: CapturedOutput) {
        val owner = UUID.randomUUID()
        mvc.perform(get("/api/customers")).andExpect(status().isUnauthorized)
        mvc.perform(
            post("/api/customers").with(user(owner)).header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("""{"name":" ","email":"bad"}""")
        )
            .andExpect(status().isBadRequest)
        val id = createCustomer(owner)
        for (request in listOf(
            get("/api/customers/$id"),
            put("/api/customers/$id").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Changed","email":"changed@example.com","revision":1}"""),
            delete("/api/customers/$id?revision=1")
        )) {
            mvc.perform(request.with(user(UUID.randomUUID()))).andExpect(status().isNotFound)
        }
        mvc.perform(get("/api/customers").with(user(owner))).andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
        mvc.perform(get("/api/customers?after=$id").with(user(owner))).andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
        mvc.perform(get("/api/customers?limit=101").with(user(owner))).andExpect(status().isBadRequest)
        mvc.perform(get("/api/customers/invalid").with(user(owner))).andExpect(status().isBadRequest)
        mvc.perform(
            put("/api/customers/$id").with(user(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Changed","email":"changed@example.com","revision":99}""")
        )
            .andExpect(status().isConflict)
        mvc.perform(delete("/api/customers/$id?revision=99").with(user(owner))).andExpect(status().isConflict)
        mvc.perform(
            post("/api/customers").with(user(owner)).header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Duplicate","email":"CUSTOMER-$owner@EXAMPLE.COM"}""")
        )
            .andExpect(status().isConflict)
        mvc.perform(
            post("/api/customers").with(user(owner)).header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Unexpected","email":"another@example.com","ownerId":"$owner"}""")
        )
            .andExpect(status().isBadRequest)
        assertFalse(output.toString().contains("customer-$owner@example.com"))
        assertFalse(output.toString().contains("CUSTOMER-$owner@EXAMPLE.COM"))
        assertEquals(1, count("customer_outbox", id))
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM customers WHERE owner_id=?", Int::class.java, owner))
        mvc.perform(post("/api/admin/customer-delivery/${event(id, 1).eventId}/replay").with(user(owner)))
            .andExpect(status().isForbidden)
        drain()
    }

    @Test
    fun `parallel POST requests with same key create one customer and one event`() {
        val owner = UUID.randomUUID();
        val requestKey = UUID.randomUUID()
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val results = (1..2).map {
                executor.submit(Callable {
                    start.await()
                    mvc.perform(
                        post("/api/customers").with(user(owner)).header("Idempotency-Key", requestKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"name":"Synthetic","email":"same-$owner@example.com"}""")
                    )
                        .andExpect(status().isCreated).andReturn().response.contentAsString
                })
            }
            start.countDown()
            assertEquals(results[0].get(), results[1].get())
        }
        val id = jdbc.queryForObject("SELECT id FROM customers WHERE owner_id=?", UUID::class.java, owner)!!
        assertEquals(1, count("customer_outbox", id))
        mvc.perform(
            post("/api/customers").with(user(owner)).header("Idempotency-Key", requestKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Different","email":"same-$owner@example.com"}""")
        )
            .andExpect(status().isConflict)
        mvc.perform(delete("/api/customers/$id?revision=1").with(user(owner))).andExpect(status().isNoContent)
        mvc.perform(
            post("/api/customers").with(user(owner)).header("Idempotency-Key", requestKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Synthetic","email":"same-$owner@example.com"}""")
        )
            .andExpect(status().isConflict)
        drain()
    }

    @Test
    fun `parallel updates on same revision have one winner and preserve event sequence`() {
        val owner = UUID.randomUUID();
        val id = createCustomer(owner)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val results = (1..2).map { n ->
                executor.submit(Callable {
                    start.await()
                    mvc.perform(
                        put("/api/customers/$id").with(user(owner)).contentType(MediaType.APPLICATION_JSON)
                            .content("""{"name":"Changed $n","email":"changed-$n@example.com","revision":1}""")
                    )
                        .andReturn().response.status
                })
            }
            start.countDown(); assertEquals(listOf(200, 409), results.map { it.get() }.sorted())
        }
        assertEquals(2, count("customer_outbox", id)); drain()
        await { count("customer_audit", id) == 2 }
    }

    @Test
    fun `rollback removes customer outbox and request reservation and does not evict committed cache`() {
        val owner = UUID.randomUUID()
        assertFailsWith<IllegalStateException> {
            TransactionTemplate(txManager).executeWithoutResult {
                create.execute(CreateCustomerCommand(owner, "Rolled back", "rollback@example.com", UUID.randomUUID()))
                error("Rollback transaction")
            }
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM customers WHERE owner_id=?", Int::class.java, owner))
        assertEquals(
            0,
            jdbc.queryForObject("SELECT count(*) FROM customer_outbox WHERE owner_id=?", Int::class.java, owner)
        )
        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT count(*) FROM customer_creation_requests WHERE owner_id=?",
                Int::class.java,
                owner
            )
        )
        val id = createCustomer(owner)
        mvc.perform(get("/api/customers/$id").with(user(owner))).andExpect(status().isOk)
        assertFailsWith<IllegalStateException> {
            TransactionTemplate(txManager).executeWithoutResult {
                update.execute(UpdateCustomerCommand(id, owner, "Rollback update", "rollback-update@example.com", 1))
                error("Rollback transaction")
            }
        }
        assertNotNull(redisTemplate.opsForValue().get(key(id, 1)))
        assertEquals(1, count("customer_outbox", id)); drain()
    }

    @Test
    fun `cache TTL corrupt entry fallback and Redis outage preserve reads and confirmed writes`() {
        val owner = UUID.randomUUID();
        val id = createCustomer(owner)
        mvc.perform(get("/api/customers/$id").with(user(owner))).andExpect(status().isOk)
        await { redisTemplate.opsForValue().get(key(id, 1)) == null }
        redisTemplate.opsForValue().set(key(id, 1), "corrupted", Duration.ofSeconds(2))
        mvc.perform(get("/api/customers/$id").with(user(owner))).andExpect(status().isOk)
        redis.dockerClient.pauseContainerCmd(redis.containerId).exec()
        try {
            mvc.perform(get("/api/customers/$id").with(user(owner))).andExpect(status().isOk)
            mvc.perform(
                put("/api/customers/$id").with(user(owner)).contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Redis offline","email":"redis-offline@example.com","revision":1}""")
            )
                .andExpect(status().isOk)
        } finally {
            redis.dockerClient.unpauseContainerCmd(redis.containerId).exec()
        }
        mvc.perform(get("/api/customers/$id").with(user(owner)))
            .andExpect(status().isOk).andExpect(jsonPath("$.revision").value(2))
        assertTrue(meters.get("customer.cache.failures").tag("operation", "get").counter().count() > 0)
        drain()
    }

    @Test
    fun `Kafka outage persists backoff blocks later revisions and recovers after admin retry`() {
        drain()
        val owner = UUID.randomUUID();
        val id = createCustomer(owner)
        update.execute(UpdateCustomerCommand(id, owner, "Changed", "kafka-outage@example.com", 1))
        val first = event(id, 1);
        val second = event(id, 2)
        kafka.dockerClient.pauseContainerCmd(kafka.containerId).exec()
        try {
            assertTrue(publish.execute())
            assertEquals("PENDING", statusOf(first.eventId)); assertEquals("PENDING", statusOf(second.eventId))
            assertFalse(publish.execute()) // not due; later revision must remain blocked
            jdbc.update("UPDATE customer_outbox SET next_attempt_at=CURRENT_TIMESTAMP WHERE event_id=?", first.eventId)
            assertTrue(publish.execute()); assertEquals("FAILED", statusOf(first.eventId))
            assertFalse(publish.execute())
        } finally {
            kafka.dockerClient.unpauseContainerCmd(kafka.containerId).exec()
        }
        mvc.perform(post("/api/admin/customer-delivery/${first.eventId}/retry").with(admin()))
            .andExpect(status().isAccepted)
        drain()
        await { count("customer_audit", id) == 2 && count("customer_notifications", id) == 2 }
        assertEquals("PUBLISHED", statusOf(first.eventId)); assertEquals("PUBLISHED", statusOf(second.eventId))
    }

    @Test
    fun `audit retries are independent notification succeeds then DLT and ordered replay repair audit gap`() {
        drain()
        val owner = UUID.randomUUID();
        val id = createCustomer(owner)
        val first = event(id, 1)
        failingAudit.failures[first.eventId] = AtomicInteger(100)
        update.execute(UpdateCustomerCommand(id, owner, "Changed", "dlt@example.com", 1))
        val second = event(id, 2)
        drain()
        await { count("customer_notifications", id) == 2 }
        await {
            meters.find("customer.consumer.dlt").tag("consumer", "audit").counter()?.count()?.let { it >= 2 } ?: false
        }
        assertEquals(0, count("customer_audit", id))
        val records = readDlt(id)
        assertTrue(records.size >= 2)
        records.forEach {
            assertFalse(it.value().contains("email")); assertFalse(it.value().contains("example.com"))
            assertNotNull(it.headers().lastHeader("original-offset"))
            assertNull(it.headers().lastHeader("kafka_dlt-exception-message"))
        }
        assertTrue(failingAudit.failures[first.eventId]!!.get() <= 96) // initial attempt + 3 retries
        failingAudit.failures.clear()
        mvc.perform(post("/api/admin/customer-delivery/${first.eventId}/replay").with(admin()))
            .andExpect(status().isAccepted)
        await { count("customer_audit", id) == 1 }
        mvc.perform(post("/api/admin/customer-delivery/${second.eventId}/replay").with(admin()))
            .andExpect(status().isAccepted)
        await { count("customer_audit", id) == 2 }
        assertEquals(2, count("customer_notifications", id))
    }

    @Test
    fun `transient audit failure retries and malformed messages go to both minimized DLTs`() {
        val owner = UUID.randomUUID();
        val id = createCustomer(owner)
        val first = event(id, 1)
        failingAudit.failures[first.eventId] = AtomicInteger(1)
        drain()
        await { count("customer_audit", id) == 1 && count("customer_notifications", id) == 1 }
        assertTrue(failingAudit.failures[first.eventId]!!.get() <= 0)
        val beforeAudit = meters.find("customer.consumer.dlt").tag("consumer", "audit").counter()?.count() ?: 0.0
        val beforeNotification =
            meters.find("customer.consumer.dlt").tag("consumer", "notification").counter()?.count() ?: 0.0
        val producer = org.apache.kafka.clients.producer.KafkaProducer<String, String>(
            mapOf(
                "bootstrap.servers" to kafka.bootstrapServers,
                "key.serializer" to "org.apache.kafka.common.serialization.StringSerializer",
                "value.serializer" to "org.apache.kafka.common.serialization.StringSerializer"
            )
        )
        producer.use {
            it.send(
                org.apache.kafka.clients.producer.ProducerRecord(
                    "customer.changes.v1",
                    id.toString(),
                    """{"email":"rejected-sensitive@example.com"}"""
                )
            ).get()
        }
        await {
            (meters.find("customer.consumer.dlt").tag("consumer", "audit").counter()?.count() ?: 0.0) > beforeAudit &&
                    (meters.find("customer.consumer.dlt").tag("consumer", "notification").counter()?.count()
                        ?: 0.0) > beforeNotification
        }
        assertEquals(1, count("customer_audit", id)); assertEquals(1, count("customer_notifications", id))
    }

    @Test
    fun `receipt retention keeps live cursor and deduplication across long inactivity`() {
        val owner = UUID.randomUUID();
        val id = createCustomer(owner)
        val first = event(id, 1)
        drain(); await { count("customer_audit", id) == 1 && count("customer_notifications", id) == 1 }
        for (table in listOf("customer_audit", "customer_notifications")) {
            jdbc.update("UPDATE $table SET recorded_at=CURRENT_TIMESTAMP-INTERVAL '32 days' WHERE customer_id=?", id)
            jdbc.update(
                "UPDATE ${table}_cursor SET updated_at=CURRENT_TIMESTAMP-INTERVAL '40 days' WHERE customer_id=?",
                id
            )
        }
        purgeAudit.execute(); purgeNotifications.execute()
        assertEquals(0, count("customer_audit", id)); assertEquals(0, count("customer_notifications", id))
        assertEquals(1, count("customer_audit_cursor", id)); assertEquals(1, count("customer_notifications_cursor", id))
        audit.execute(first); notifications.execute(first)
        assertEquals(0, count("customer_audit", id)); assertEquals(0, count("customer_notifications", id))
        update.execute(UpdateCustomerCommand(id, owner, "Active again", "active-again@example.com", 1))
        drain(); await { count("customer_audit", id) == 1 && count("customer_notifications", id) == 1 }
        mvc.perform(delete("/api/customers/$id?revision=2").with(user(owner))).andExpect(status().isNoContent)
        drain(); await { count("customer_audit", id) == 2 && count("customer_notifications", id) == 2 }
        for (table in listOf("customer_audit_cursor", "customer_notifications_cursor")) {
            jdbc.update("UPDATE $table SET deleted_at=CURRENT_TIMESTAMP-INTERVAL '32 days' WHERE customer_id=?", id)
        }
        purgeAudit.execute(); purgeNotifications.execute()
        assertEquals(0, count("customer_audit_cursor", id)); assertEquals(0, count("customer_notifications_cursor", id))
        assertFailsWith<IllegalArgumentException> {
            audit.execute(
                first.copy(
                    occurredAt = java.time.Instant.now().minusSeconds(31 * 86400L)
                )
            )
        }
    }

    @Test
    fun `consumer transaction rollback keeps cursor and effect atomic`() {
        val owner = UUID.randomUUID();
        val id = createCustomer(owner)
        val first = event(id, 1)
        assertFailsWith<IllegalStateException> {
            TransactionTemplate(txManager).executeWithoutResult {
                audit.execute(first)
                error("Synthetic rollback after effect")
            }
        }
        assertEquals(0, count("customer_audit", id)); assertEquals(0, count("customer_audit_cursor", id))
        audit.execute(first)
        assertEquals(1, count("customer_audit", id)); assertEquals(1, count("customer_audit_cursor", id))
        drain(); await { count("customer_notifications", id) == 1 }
        assertEquals(1, count("customer_audit", id))
    }

    @Test
    fun `retention removes inactive profile through normal deletion flow`() {
        val owner = UUID.randomUUID();
        val id = createCustomer(owner)
        jdbc.update("UPDATE customers SET updated_at=CURRENT_TIMESTAMP-INTERVAL '366 days' WHERE id=?", id)
        expired.execute()
        assertEquals(0, count("customers", id, "id")); assertEquals(2, count("customer_outbox", id))
        assertEquals("customer.deleted.v1", event(id, 2).type)
        drain(); await { count("customer_audit", id) == 2 }
    }

    private fun createCustomer(owner: UUID): UUID = create.execute(
        CreateCustomerCommand(
            owner,
            "Synthetic Customer", "customer-$owner@example.com", UUID.randomUUID()
        )
    ).id

    private fun signupAndLogin(marker: UUID): String {
        val email = "account-$marker@example.com"
        mvc.perform(
            post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Synthetic Owner","email":"$email","password":"valid-password"}""")
        )
            .andExpect(status().isCreated)
        val result = mvc.perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"valid-password"}""")
        )
            .andExpect(status().isOk).andReturn().response.contentAsString
        return mapper.readTree(result)["accessToken"].asString()
    }

    private fun user(owner: UUID) =
        jwt().jwt { it.subject(owner.toString()) }.authorities(SimpleGrantedAuthority("ROLE_USER"))

    private fun admin() =
        jwt().jwt { it.subject(UUID.randomUUID().toString()) }.authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    private fun key(id: UUID, revision: Long) = "customer:v1:$id:$revision"
    private fun count(table: String, id: UUID, column: String = "customer_id") =
        jdbc.queryForObject("SELECT count(*) FROM $table WHERE $column=?", Int::class.java, id) ?: 0

    private fun statusOf(eventId: UUID) =
        jdbc.queryForObject("SELECT status FROM customer_outbox WHERE event_id=?", String::class.java, eventId)

    private fun event(id: UUID, revision: Long) =
        jdbc.query("SELECT * FROM customer_outbox WHERE customer_id=? AND revision=?", { rs, _ ->
            CustomerChange(
                rs.getObject("event_id", UUID::class.java),
                rs.getObject("customer_id", UUID::class.java),
                rs.getObject("owner_id", UUID::class.java),
                rs.getLong("revision"),
                rs.getString("event_type"),
                rs.getTimestamp("occurred_at").toInstant()
            )
        }, id, revision).single()

    private fun drain() {
        repeat(200) { if (!publish.execute()) return }; error("Outbox did not drain")
    }

    private fun await(check: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(40).toNanos()
        while (System.nanoTime() < deadline) {
            if (check()) return; Thread.sleep(50)
        }
        assertTrue(check(), "Expected eventual condition before timeout")
    }

    private fun readDlt(id: UUID): List<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>> {
        val records = mutableListOf<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>>()
        KafkaConsumer<String, String>(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "test-dlt-${UUID.randomUUID()}",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java
            )
        ).use { consumer ->
            consumer.subscribe(listOf("customer.changes.v1.audit.DLT"))
            await {
                records.addAll(
                    consumer.poll(Duration.ofMillis(200)).filter { it.key() == id.toString() }); records.size >= 2
            }
        }
        return records
    }

    @TestConfiguration(proxyBeanMethods = false)
    class FailureConfiguration {
        @Bean
        @Primary
        fun failingAudit(delegate: JdbcCustomerAudit) = FailingAudit(delegate)
    }

    class FailingAudit(private val delegate: JdbcCustomerAudit) : CustomerAudit {
        val failures = ConcurrentHashMap<UUID, AtomicInteger>()
        override fun record(change: CustomerChange): Boolean {
            if ((failures[change.eventId]?.getAndDecrement() ?: 0) > 0) error("Synthetic audit failure")
            return delegate.record(change)
        }

        override fun purge() = delegate.purge()
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
        @Container
        @ServiceConnection(name = "redis")
        @JvmStatic
        val redis = GenericContainer(DockerImageName.parse("redis:8-alpine")).withExposedPorts(6379)
        @Container
        @ServiceConnection
        @JvmStatic
        val kafka = KafkaContainer(DockerImageName.parse("apache/kafka:4.1.2"))
    }
}
