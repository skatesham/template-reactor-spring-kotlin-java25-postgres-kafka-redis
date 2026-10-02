package com.kotlin.template.api

import com.kotlin.template.TestcontainersConfiguration
import com.kotlin.template.audit.interfaces.scheduler.AuditRetentionJob
import com.kotlin.template.customer.interfaces.scheduler.CustomerJobs
import com.kotlin.template.notification.interfaces.scheduler.NotificationRetentionJob
import io.micrometer.core.instrument.MeterRegistry
import io.restassured.RestAssured.given
import io.restassured.builder.RequestSpecBuilder
import io.restassured.http.ContentType
import io.restassured.response.Response
import io.restassured.specification.RequestSpecification
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.*
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.test.annotation.DirtiesContext
import org.testcontainers.kafka.KafkaContainer
import tools.jackson.databind.ObjectMapper

/** Real sockets, real JWTs, real infrastructure; no mocked security or global RestAssured state. */
@Import(TestcontainersConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = [
        "app.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.security.jwt.issuer=rest-assured-tests",
        "app.security.cors.allowed-origins=https://trusted.example",
        "spring.datasource.password=test",
        "spring.docker.compose.enabled=false",
        "app.customer.outbox.poll-ms=50",
    ]
)
class RestAssuredIntegrationTests {
    @LocalServerPort
    var port: Int = 0
    @Autowired
    lateinit var mapper: ObjectMapper
    @Autowired
    lateinit var jdbc: JdbcTemplate
    @Autowired
    lateinit var redis: StringRedisTemplate
    @Autowired
    lateinit var encoder: JwtEncoder
    @Autowired
    lateinit var meters: MeterRegistry
    @Autowired
    lateinit var jobs: CustomerJobs
    @Autowired
    lateinit var auditRetention: AuditRetentionJob
    @Autowired
    lateinit var notificationRetention: NotificationRetentionJob
    @Autowired
    lateinit var kafka: KafkaContainer
    private lateinit var base: RequestSpecification

    @BeforeEach
    fun http() {
        base = RequestSpecBuilder().setBaseUri("http://localhost").setPort(port)
            .setContentType(ContentType.JSON).build()
        // Deliberately no request/response logging: Authorization and credentials must stay private.
    }

    @Test
    fun `signup login me and JWT claims expose only required fields`() {
        val account = account()
        request(account.token).get("/api/users/me").then().statusCode(200)
            .body("id", equalTo(account.id.toString()), "email", equalTo(account.email), "roles", hasItem("USER"))
            .body("password", nullValue(), "passwordHash", nullValue())
            .header("Cache-Control", containsString("no-store"))
        val claims = mapper.readTree(java.util.Base64.getUrlDecoder().decode(account.token.split('.')[1]))
        assertEquals(account.id.toString(), claims["sub"].asString())
        assertFalse(claims.has("name")); assertFalse(claims.has("email"))
        assertNotEquals(
            account.password,
            jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?", String::class.java, account.id)
        )
        request().body(json("email" to account.email.uppercase(), "password" to account.password))
            .post("/api/auth/login").then().statusCode(200)
            .body("tokenType", equalTo("Bearer"), "expiresIn", equalTo(900))
    }

    @ParameterizedTest
    @ValueSource(strings = ["blank-name", "long-name", "bad-email", "long-email", "short-password", "long-password", "roles", "missing-field", "null-field"])
    fun `signup rejects invalid contracts without exposing rejected values`(scenario: String) {
        val payload = mutableMapOf<String, Any?>(
            "name" to "Synthetic Owner",
            "email" to "synthetic-${UUID.randomUUID()}@example.com",
            "password" to "synthetic-password"
        )
        when (scenario) {
            "blank-name" -> payload["name"] = " "
            "long-name" -> payload["name"] = "n".repeat(101)
            "bad-email" -> payload["email"] = "invalid"
            "long-email" -> payload["email"] = "a".repeat(243) + "@example.com"
            "short-password" -> payload["password"] = "short"
            "long-password" -> payload["password"] = "p".repeat(129)
            "roles" -> payload["roles"] = listOf("ADMIN")
            "missing-field" -> payload.remove("name")
            "null-field" -> payload["name"] = null
        }
        val response = request().body(mapper.writeValueAsString(payload)).post("/api/auth/signup")
        problem(response, 400)
        assertFalse(response.asString().contains("synthetic-password"))
        assertFalse(response.asString().contains("passwordHash"))
    }

    @Test
    fun `boundary names and multibyte passwords work without truncation`() {
        val email = "boundary-${UUID.randomUUID()}@example.com"
        val password = "ç".repeat(120) + "original"
        request().body(json("name" to "n".repeat(100), "email" to email, "password" to password))
            .post("/api/auth/signup").then().statusCode(201).body("name.length()", equalTo(100))
        request().body(json("email" to email, "password" to password)).post("/api/auth/login").then().statusCode(200)
        problem(
            request().body(json("email" to email, "password" to "ç".repeat(120) + "modified")).post("/api/auth/login"),
            401
        )
    }

    @Test
    fun `duplicate signup and invalid credentials have stable error contracts`() {
        val account = account()
        problem(
            request().body(
                json(
                    "name" to "Duplicate",
                    "email" to account.email.uppercase(),
                    "password" to account.password
                )
            ).post("/api/auth/signup"), 409
        )
        val wrong =
            request().body(json("email" to account.email, "password" to "incorrect-password")).post("/api/auth/login")
        val unknown = request().body(
            json(
                "email" to "missing-${UUID.randomUUID()}@example.com",
                "password" to "incorrect-password"
            )
        ).post("/api/auth/login")
        problem(wrong, 401); problem(unknown, 401)
        assertEquals(wrong.jsonPath().getString("detail"), unknown.jsonPath().getString("detail"))
    }

    @Test
    fun `missing tampered expired and wrong issuer JWTs fail authentication on real HTTP`() {
        problem(request().get("/api/users/me"), 401)
        val account = account()
        val parts = account.token.split('.')
        val tampered = parts.take(2).joinToString(".") + "." + (if (parts[2][0] == 'a') "b" else "a") + parts[2].drop(1)
        val now = Instant.now()
        val expired = encode(account.id, "rest-assured-tests", now.minusSeconds(3600), now.minusSeconds(600))
        val wrongIssuer = encode(account.id, "untrusted", now, now.plusSeconds(600))
        for (token in listOf(tampered, expired, wrongIssuer, "invalid")) {
            val response = request(token).get("/api/customers")
            problem(response, 401)
            response.then().header("WWW-Authenticate", equalTo("Bearer"))
        }
    }

    @Test
    fun `deleted identity yields a safe not found response`() {
        val account = account()
        jdbc.update("DELETE FROM user_roles WHERE user_id=?", account.id)
        jdbc.update("DELETE FROM users WHERE id=?", account.id)
        problem(request(account.token).get("/api/users/me"), 404)
    }

    @Test
    fun `customer CRUD cache and automatic Kafka delivery complete over real sockets`() {
        val account = account()
        val created = create(account.token)
        val id = UUID.fromString(created.jsonPath().getString("id"))
        assertEquals(7, id.version())
        created.then().header("Location", equalTo("/api/customers/$id"))
            .body("revision", equalTo(1), "ownerId", nullValue())
        request(account.token).get("/api/customers/$id").then().statusCode(200)
        assertNotNull(redis.opsForValue().get("customer:v1:$id:1"))
        request(account.token).get("/api/customers/$id").then().statusCode(200)
            .body("name", equalTo("Synthetic Customer"))
        request(account.token).body(
            json(
                "name" to "Updated Customer",
                "email" to "updated-${UUID.randomUUID()}@example.com",
                "revision" to 1
            )
        )
            .put("/api/customers/$id").then().statusCode(200)
            .body("revision", equalTo(2), "name", equalTo("Updated Customer"))
        assertNull(redis.opsForValue().get("customer:v1:$id:1"))
        request(account.token).get("/api/customers/$id").then().statusCode(200).body("revision", equalTo(2))
        request(account.token).queryParam("revision", 2).delete("/api/customers/$id").then().statusCode(204)
            .body(equalTo(""))
        assertNull(redis.opsForValue().get("customer:v1:$id:2"))
        problem(request(account.token).get("/api/customers/$id"), 404)
        await { count("customer_audit", id) == 3 && count("customer_notifications", id) == 3 }
        assertEquals(
            listOf(1L, 2L, 3L),
            jdbc.queryForList(
                "SELECT revision FROM customer_audit WHERE customer_id=? ORDER BY recorded_at",
                Long::class.java,
                id
            )
        )
        request(account.token).get("/api/notifications").then().statusCode(200)
            .body("size()", equalTo(3), "[0].type", equalTo("customer.deleted.v1"))
        assertEquals(
            3,
            jdbc.queryForObject(
                "SELECT count(*) FROM customer_outbox WHERE customer_id=? AND status='PUBLISHED'",
                Int::class.java,
                id
            )
        )
    }

    @Test
    fun `ownership isolates reads lists mutations notifications and email uniqueness`() {
        val owner = account();
        val other = account()
        val email = "shared-${UUID.randomUUID()}@example.com"
        val id = create(owner.token, email = email).jsonPath().getString("id")
        problem(request(other.token).get("/api/customers/$id"), 404)
        problem(
            request(other.token).body(json("name" to "Forbidden", "email" to email, "revision" to 1))
                .put("/api/customers/$id"), 404
        )
        problem(request(other.token).queryParam("revision", 1).delete("/api/customers/$id"), 404)
        request(other.token).get("/api/customers").then().statusCode(200).body("size()", equalTo(0))
        request(other.token).get("/api/notifications").then().statusCode(200).body("size()", equalTo(0))
        create(other.token, email = email).then().statusCode(201) // uniqueness is owner scoped
        problem(
            request(owner.token).header("Idempotency-Key", UUID.randomUUID())
                .body(json("name" to "Duplicate", "email" to email.uppercase())).post("/api/customers"), 409
        )
        assertEquals(1, count("customer_outbox", UUID.fromString(id)))
    }

    @ParameterizedTest
    @ValueSource(strings = ["blank-name", "long-name", "bad-email", "long-email", "unknown-field", "null-field", "missing-field"])
    fun `customer create validation rejects malformed profiles atomically`(scenario: String) {
        val account = account()
        val fields =
            mutableMapOf<String, Any?>("name" to "Synthetic", "email" to "customer-${UUID.randomUUID()}@example.com")
        when (scenario) {
            "blank-name" -> fields["name"] = " "
            "long-name" -> fields["name"] = "n".repeat(101)
            "bad-email" -> fields["email"] = "invalid"
            "long-email" -> fields["email"] = "e".repeat(243) + "@example.com"
            "unknown-field" -> fields["ownerId"] = UUID.randomUUID()
            "null-field" -> fields["email"] = null
            "missing-field" -> fields.remove("email")
        }
        problem(
            request(account.token).header("Idempotency-Key", UUID.randomUUID()).body(mapper.writeValueAsString(fields))
                .post("/api/customers"), 400
        )
        assertEquals(
            0,
            jdbc.queryForObject("SELECT count(*) FROM customers WHERE owner_id=?", Int::class.java, account.id)
        )
        assertEquals(
            0,
            jdbc.queryForObject("SELECT count(*) FROM customer_outbox WHERE owner_id=?", Int::class.java, account.id)
        )
    }

    @Test
    fun `creation key is required valid scoped and prevents resurrection after deletion`() {
        val account = account();
        val other = account()
        val body = json("name" to "Synthetic", "email" to "key-${UUID.randomUUID()}@example.com")
        problem(request(account.token).body(body).post("/api/customers"), 400)
        problem(request(account.token).header("Idempotency-Key", "bad").body(body).post("/api/customers"), 400)
        val key = UUID.randomUUID()
        val first = request(account.token).header("Idempotency-Key", key).body(body).post("/api/customers")
        first.then().statusCode(201)
        val second = request(account.token).header("Idempotency-Key", key).body(body).post("/api/customers")
        second.then().statusCode(201); assertEquals(first.asString(), second.asString())
        val id = first.jsonPath().getString("id")
        val otherId =
            request(other.token).header("Idempotency-Key", key).body(body).post("/api/customers").then().statusCode(201)
                .extract().path<String>("id")
        assertNotEquals(id, otherId)
        problem(
            request(account.token).header("Idempotency-Key", key)
                .body(json("name" to "Changed", "email" to "other@example.com")).post("/api/customers"), 409
        )
        request(account.token).queryParam("revision", 1).delete("/api/customers/$id").then().statusCode(204)
        problem(request(account.token).header("Idempotency-Key", key).body(body).post("/api/customers"), 409)
        problem(request(account.token).queryParam("revision", 2).delete("/api/customers/$id"), 404)
        assertEquals(2, count("customer_outbox", UUID.fromString(id)))
    }

    @Test
    fun `parallel HTTP retries have one creation and one event`() {
        val account = account();
        val key = UUID.randomUUID()
        val body = json("name" to "Concurrent", "email" to "concurrent-${UUID.randomUUID()}@example.com")
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val results = (1..2).map {
                executor.submit(Callable {
                    start.await()
                    request(account.token).header("Idempotency-Key", key).body(body).post("/api/customers").then()
                        .statusCode(201).extract().asString()
                })
            }
            start.countDown()
            val payload = results.map { it.get() }
            assertEquals(payload[0], payload[1])
            assertEquals(1, count("customer_outbox", UUID.fromString(mapper.readTree(payload[0])["id"].asString())))
        }
    }

    @Test
    fun `parallel HTTP updates reject lost updates and preserve revisions`() {
        val account = account();
        val id = create(account.token).jsonPath().getString("id")
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val results = (1..2).map { n ->
                executor.submit(Callable {
                    start.await()
                    request(account.token).body(
                        json(
                            "name" to "Changed $n",
                            "email" to "updated-$n-${UUID.randomUUID()}@example.com",
                            "revision" to 1
                        )
                    )
                        .put("/api/customers/$id").statusCode()
                })
            }
            start.countDown(); assertEquals(listOf(200, 409), results.map { it.get() }.sorted())
        }
        assertEquals(2, count("customer_outbox", UUID.fromString(id)))
    }

    @ParameterizedTest
    @ValueSource(strings = ["zero", "negative", "missing", "name", "email", "unknown"])
    fun `update validation leaves profile and outbox unchanged`(scenario: String) {
        val account = account();
        val id = create(account.token).jsonPath().getString("id")
        val fields = mutableMapOf<String, Any?>("name" to "Changed", "email" to "changed@example.com", "revision" to 1)
        when (scenario) {
            "zero" -> fields["revision"] = 0
            "negative" -> fields["revision"] = -1
            "missing" -> fields.remove("revision")
            "name" -> fields["name"] = " "
            "email" -> fields["email"] = "invalid"
            "unknown" -> fields["roles"] = listOf("ADMIN")
        }
        problem(request(account.token).body(mapper.writeValueAsString(fields)).put("/api/customers/$id"), 400)
        request(account.token).get("/api/customers/$id").then().statusCode(200)
            .body("revision", equalTo(1), "name", equalTo("Synthetic Customer"))
        assertEquals(1, count("customer_outbox", UUID.fromString(id)))
    }

    @Test
    fun `stale revisions and duplicate email updates return conflict without new facts`() {
        val account = account();
        val email = "unique-${UUID.randomUUID()}@example.com"
        val first = create(account.token, email = email).jsonPath().getString("id")
        val second = create(account.token).jsonPath().getString("id")
        problem(
            request(account.token).body(json("name" to "Changed", "email" to email, "revision" to 1))
                .put("/api/customers/$second"), 409
        )
        problem(
            request(account.token).body(
                json(
                    "name" to "Changed",
                    "email" to "changed@example.com",
                    "revision" to 9
                )
            ).put("/api/customers/$first"), 409
        )
        problem(request(account.token).queryParam("revision", 9).delete("/api/customers/$first"), 409)
        assertEquals(1, count("customer_outbox", UUID.fromString(first)))
        assertEquals(1, count("customer_outbox", UUID.fromString(second)))
    }

    @Test
    fun `pagination validation invalid ids and missing resources use safe errors`() {
        val account = account()
        val ids = (1..3).map { create(account.token).jsonPath().getString("id") }.sorted()
        val page = request(account.token).queryParam("limit", 2).get("/api/customers").then().statusCode(200)
            .body("size()", equalTo(2)).extract().path<List<String>>("id")
        assertEquals(ids.take(2), page)
        request(account.token).queryParam("limit", 2).queryParam("after", page.last()).get("/api/customers").then()
            .statusCode(200).body("id", equalTo(ids.takeLast(1)))
        for (limit in listOf("0", "-1", "101", "bad")) problem(
            request(account.token).queryParam("limit", limit).get("/api/customers"), 400
        )
        problem(request(account.token).queryParam("after", "bad").get("/api/customers"), 400)
        problem(request(account.token).get("/api/customers/not-a-uuid"), 400)
        val absent = UUID.randomUUID()
        problem(request(account.token).get("/api/customers/$absent"), 404)
        problem(
            request(account.token).body(
                json(
                    "name" to "Missing",
                    "email" to "missing@example.com",
                    "revision" to 1
                )
            ).put("/api/customers/$absent"), 404
        )
        problem(request(account.token).queryParam("revision", 1).delete("/api/customers/$absent"), 404)
        for (revision in listOf("0", "-1", "bad")) problem(
            request(account.token).queryParam("revision", revision).delete("/api/customers/${ids.first()}"), 400
        )
        problem(request(account.token).delete("/api/customers/${ids.first()}"), 400)
    }

    @Test
    fun `malformed JSON media types and unsupported methods return appropriate HTTP errors`() {
        val account = account()
        problem(
            request(account.token).header("Idempotency-Key", UUID.randomUUID()).body("{").post("/api/customers"),
            400
        )
        request(account.token).header("Idempotency-Key", UUID.randomUUID()).contentType(ContentType.TEXT)
            .body("not JSON").post("/api/customers").then().statusCode(415)
        request(account.token).patch("/api/customers/${UUID.randomUUID()}").then().statusCode(405)
    }

    @Test
    fun `administrative retry replay and metrics require fresh ADMIN token and deduplicate effects`() {
        val account = account();
        val id = UUID.fromString(create(account.token).jsonPath().getString("id"))
        await { count("customer_audit", id) == 1 && count("customer_notifications", id) == 1 }
        val eventId =
            jdbc.queryForObject("SELECT event_id FROM customer_outbox WHERE customer_id=?", UUID::class.java, id)!!
        for (action in listOf(
            "retry",
            "replay"
        )) problem(request(account.token).post("/api/admin/customer-delivery/$eventId/$action"), 403)
        problem(request(account.token).get("/actuator/metrics"), 403)
        jdbc.update("INSERT INTO user_roles(user_id, role_name) VALUES (?, 'ADMIN')", account.id)
        val admin = login(account.email, account.password)
        problem(request(account.token).get("/actuator/metrics"), 403) // issued token retains its original roles
        request(admin).get("/actuator/metrics").then().statusCode(200).body("names", hasItem("http.server.requests"))
        for (metric in listOf("customer.outbox.pending", "customer.outbox.failed", "customer.outbox.oldest.seconds")) {
            val measured = request(admin).get("/actuator/metrics/$metric").then().statusCode(200)
                .body("name", equalTo(metric), "measurements[0].value", notNullValue()).extract().response()
            assertTrue(measured.jsonPath().getDouble("measurements[0].value") >= 0)
        }
        for (action in listOf(
            "retry",
            "replay"
        )) problem(request(admin).post("/api/admin/customer-delivery/${UUID.randomUUID()}/$action"), 404)
        problem(request(admin).post("/api/admin/customer-delivery/$eventId/retry"), 404) // already published
        val before = duplicates()
        request(admin).post("/api/admin/customer-delivery/$eventId/replay").then().statusCode(202)
        await { duplicates() >= before + 2 }
        jdbc.update("UPDATE customer_outbox SET status='FAILED' WHERE event_id=?", eventId)
        request(admin).post("/api/admin/customer-delivery/$eventId/retry").then().statusCode(202)
        await {
            jdbc.queryForObject(
                "SELECT status FROM customer_outbox WHERE event_id=?",
                String::class.java,
                eventId
            ) == "PUBLISHED"
        }
        assertEquals(1, count("customer_audit", id)); assertEquals(1, count("customer_notifications", id))
    }

    @Test
    fun `Kafka unavailable during administrative replay returns safe service unavailable`() {
        val account = account();
        val id = UUID.fromString(create(account.token).jsonPath().getString("id"))
        await { count("customer_audit", id) == 1 && count("customer_notifications", id) == 1 }
        val eventId =
            jdbc.queryForObject("SELECT event_id FROM customer_outbox WHERE customer_id=?", UUID::class.java, id)!!
        jdbc.update("INSERT INTO user_roles(user_id, role_name) VALUES (?, 'ADMIN')", account.id)
        val admin = login(account.email, account.password)
        kafka.dockerClient.pauseContainerCmd(kafka.containerId).exec()
        try {
            val response = request(admin).post("/api/admin/customer-delivery/$eventId/replay")
            problem(response, 503)
            assertFalse(response.asString().contains("Kafka"))
            assertFalse(response.asString().contains(account.email))
            assertFalse(response.asString().contains("ExecutionException"))
        } finally {
            kafka.dockerClient.unpauseContainerCmd(kafka.containerId).exec()
        }
        assertEquals(
            "PUBLISHED",
            jdbc.queryForObject("SELECT status FROM customer_outbox WHERE event_id=?", String::class.java, eventId)
        )
    }

    @Test
    fun `retention removes expired profiles and reservations while active consumers keep their watermarks`() {
        val account = account()
        val inactive = UUID.fromString(create(account.token).jsonPath().getString("id"))
        val active = UUID.fromString(create(account.token).jsonPath().getString("id"))
        await {
            count("customer_audit", inactive) == 1 && count("customer_audit", active) == 1 &&
                    count("customer_notifications", inactive) == 1 && count("customer_notifications", active) == 1
        }
        request(account.token).get("/api/customers/$inactive").then().statusCode(200)
        jdbc.update("UPDATE customers SET updated_at=CURRENT_TIMESTAMP-INTERVAL '366 days' WHERE id=?", inactive)
        jdbc.update(
            "UPDATE customer_creation_requests SET created_at=CURRENT_TIMESTAMP-INTERVAL '2 days' WHERE owner_id=?",
            account.id
        )
        jdbc.update(
            "UPDATE customer_outbox SET published_at=CURRENT_TIMESTAMP-INTERVAL '31 days' WHERE customer_id IN (?,?) AND status='PUBLISHED'",
            inactive,
            active
        )
        for (table in listOf("customer_audit", "customer_notifications")) {
            jdbc.update(
                "UPDATE $table SET recorded_at=CURRENT_TIMESTAMP-INTERVAL '31 days' WHERE customer_id IN (?,?)",
                inactive,
                active
            )
        }
        jobs.retention()
        auditRetention.retention()
        notificationRetention.retention()
        problem(request(account.token).get("/api/customers/$inactive"), 404)
        request(account.token).get("/api/customers/$active").then().statusCode(200)
        assertNull(redis.opsForValue().get("customer:v1:$inactive:1"))
        assertEquals(
            0,
            jdbc.queryForObject(
                "SELECT count(*) FROM customer_creation_requests WHERE owner_id=?",
                Int::class.java,
                account.id
            )
        )
        assertEquals(0, count("customer_outbox", active))
        assertEquals(0, count("customer_audit", active)); assertEquals(0, count("customer_notifications", active))
        assertEquals(1, count("customer_audit_cursor", active)); assertEquals(
            1,
            count("customer_notifications_cursor", active)
        )
        await { count("customer_audit", inactive) == 1 && count("customer_notifications", inactive) == 1 }
        request(account.token).get("/api/notifications").then().statusCode(200)
            .body("[0].type", equalTo("customer.deleted.v1"))
        request(account.token).body(
            json(
                "name" to "Still active",
                "email" to "active-${UUID.randomUUID()}@example.com",
                "revision" to 1
            )
        )
            .put("/api/customers/$active").then().statusCode(200)
        await { count("customer_audit", active) == 1 && count("customer_notifications", active) == 1 }
    }

    @Test
    fun `CORS permits configured mutation headers and rejects other origins`() {
        request().header("Origin", "https://trusted.example").header("Access-Control-Request-Method", "PUT")
            .header("Access-Control-Request-Headers", "Authorization,Content-Type,Idempotency-Key")
            .options("/api/customers/${UUID.randomUUID()}").then().statusCode(200)
            .header("Access-Control-Allow-Origin", equalTo("https://trusted.example"))
            .header("Access-Control-Allow-Methods", containsString("PUT"))
        request().header("Origin", "https://untrusted.example").header("Access-Control-Request-Method", "DELETE")
            .options("/api/customers/${UUID.randomUUID()}").then().statusCode(403)
    }

    @Test
    fun `public health Swagger and OpenAPI document the running API`() {
        request().get("/actuator/health").then().statusCode(200)
            .body("status", equalTo("UP"), "components", nullValue())
        val document = mapper.readTree(request().get("/v3/api-docs").then().statusCode(200)
            .body("components.securitySchemes.bearerAuth.scheme", equalTo("bearer"))
            .body("paths.'/api/customers'.post", notNullValue(), "paths.'/api/notifications'.get", notNullValue())
            .extract().asString())
        val schemas = document["components"]["schemas"]
        for (name in listOf("CreateCustomerRequest", "UpdateCustomerRequest", "CustomerResponse")) {
            assertTrue(schemas[name]["description"].asString().isNotBlank(), name)
            for (property in schemas[name]["properties"]) {
                assertTrue(property["description"].asString().isNotBlank(), name)
            }
        }
        assertEquals("uuid", schemas["CustomerResponse"]["properties"]["id"]["format"].asString())
        assertEquals("date-time", schemas["CustomerResponse"]["properties"]["createdAt"]["format"].asString())
        assertTrue(schemas["UpdateCustomerRequest"]["required"].any { it.asString() == "revision" })
        val operations = listOf(
            Triple("/api/customers", "post", "201"),
            Triple("/api/customers", "get", "200"),
            Triple("/api/customers/{id}", "get", "200"),
            Triple("/api/customers/{id}", "put", "200"),
            Triple("/api/customers/{id}", "delete", "204"),
            Triple("/api/admin/customer-delivery/{eventId}/retry", "post", "202"),
            Triple("/api/admin/customer-delivery/{eventId}/replay", "post", "202"),
        )
        for ((path, method, success) in operations) {
            val operation = document["paths"][path][method]
            assertTrue(operation["summary"].asString().isNotBlank(), "$method $path")
            assertTrue(operation["description"].asString().isNotBlank(), "$method $path")
            assertTrue(operation["parameters"].none { it["name"].asString() == "jwt" }, "$method $path")
            assertEquals("object", document["components"]["schemas"]["ProblemDetail"]["type"].asString())
            assertTrue(operation["responses"].has("401"), "$method $path")
            assertTrue(operation["responses"].has("503"), "$method $path")
            assertTrue(operation["responses"]["400"]["content"].has("application/problem+json"), "$method $path")
            assertTrue(operation["responses"].has(success), "$method $path")
            if (success in listOf("202", "204")) {
                assertFalse(operation["responses"][success].has("content"), "$method $path")
            }
            if ("/admin/" in path) {
                assertTrue(operation["responses"].has("403"), "$method $path")
                assertTrue(operation["description"].asString().contains("ADMIN"), "$method $path")
            }
        }
        val create = document["paths"]["/api/customers"]["post"]
        assertTrue(create["parameters"].any {
            it["name"].asString() == "Idempotency-Key" && it["in"].asString() == "header" && it["required"].asBoolean()
        })
        assertTrue(create["responses"]["201"]["headers"].has("Location"))
        assertEquals("#/components/schemas/CreateCustomerRequest",
            create["requestBody"]["content"]["application/json"]["schema"]["\$ref"].asString())
        val list = document["paths"]["/api/customers"]["get"]
        assertEquals("array", list["responses"]["200"]["content"]["application/json"]["schema"]["type"].asString())
        assertTrue(list["parameters"].any { it["name"].asString() == "after" })
        assertTrue(list["parameters"].any { it["name"].asString() == "limit" })
        request().get("/swagger-ui/index.html").then().statusCode(200).contentType(containsString("text/html"))
    }

    private data class Account(val id: UUID, val email: String, val password: String, val token: String)

    private fun account(): Account {
        val email = "owner-${UUID.randomUUID()}@example.com";
        val password = "synthetic-password"
        val response = request().body(json("name" to "Synthetic Owner", "email" to email, "password" to password))
            .post("/api/auth/signup")
        response.then().statusCode(201)
            .body("roles", equalTo(listOf("USER")), "password", nullValue(), "passwordHash", nullValue())
        return Account(UUID.fromString(response.jsonPath().getString("id")), email, password, login(email, password))
    }

    private fun login(email: String, password: String): String =
        request().body(json("email" to email, "password" to password))
            .post("/api/auth/login").then().statusCode(200).extract().path("accessToken")

    private fun request(token: String? = null): RequestSpecification =
        given().spec(base).apply { if (token != null) auth().oauth2(token) }

    private fun json(vararg fields: Pair<String, Any?>) = mapper.writeValueAsString(mapOf(*fields))
    private fun create(token: String, email: String = "customer-${UUID.randomUUID()}@example.com") = request(token)
        .header("Idempotency-Key", UUID.randomUUID()).body(json("name" to "Synthetic Customer", "email" to email))
        .post("/api/customers").also { it.then().statusCode(201) }

    private fun problem(response: Response, status: Int) {
        response.then().statusCode(status).contentType(containsString("application/problem+json"))
            .body(
                "status",
                equalTo(status),
                "detail",
                not(emptyOrNullString()),
                "trace",
                nullValue(),
                "exception",
                nullValue()
            )
    }

    private fun encode(id: UUID, issuer: String, issued: Instant, expires: Instant): String = encoder.encode(
        JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(), JwtClaimsSet.builder().issuer(issuer).subject(id.toString())
                .issuedAt(issued).expiresAt(expires).claim("roles", listOf("USER")).build()
        )
    ).tokenValue

    private fun count(table: String, id: UUID) =
        jdbc.queryForObject("SELECT count(*) FROM $table WHERE customer_id=?", Int::class.java, id) ?: 0

    private fun duplicates() = listOf("audit", "notification").sumOf {
        meters.find("customer.consumer.records").tags("consumer", it, "result", "duplicate").counter()?.count() ?: 0.0
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
        while (System.nanoTime() < deadline) {
            if (condition()) return; Thread.sleep(50)
        }
        assertTrue(condition(), "Expected asynchronous delivery before timeout")
    }
}
