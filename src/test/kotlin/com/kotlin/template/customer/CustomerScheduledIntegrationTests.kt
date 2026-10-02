package com.kotlin.template.customer

import com.kotlin.template.TestcontainersConfiguration
import com.kotlin.template.support.TestDatabase
import java.time.Duration
import java.util.*
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.reactive.server.WebTestClient
import tools.jackson.databind.ObjectMapper

@Import(com.kotlin.template.support.ReactiveTestConfiguration::class, TestcontainersConfiguration::class)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
    properties = [
        "app.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "spring.r2dbc.password=test",
        "spring.docker.compose.enabled=false",
        "app.customer.outbox.poll-ms=50",
    ]
)
class CustomerScheduledIntegrationTests {
    @Autowired
    lateinit var client: WebTestClient
    @Autowired
    lateinit var db: TestDatabase
    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `scheduler publishes REST mutations to both consumers without manual intervention`() {
        val owner = UUID.randomUUID()
        val auth = mockJwt().jwt { it.subject(owner.toString()) }
        val response = client.mutateWith(auth).post().uri("/api/customers").header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"name":"Synthetic Customer","email":"scheduled@example.com"}""").exchange()
            .expectStatus().isCreated.expectBody().returnResult().responseBody!!.toString(Charsets.UTF_8)
        val id = UUID.fromString(mapper.readTree(response)["id"].asString())
        client.mutateWith(auth).put().uri("/api/customers/$id").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"name":"Scheduled Update","email":"scheduled-updated@example.com","revision":1}""").exchange()
            .expectStatus().isOk
        client.mutateWith(auth).delete().uri("/api/customers/$id?revision=2").exchange().expectStatus().isNoContent
        val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
        while (System.nanoTime() < deadline && !(count("customer_audit", id) == 3 && count(
                "customer_notifications",
                id
            ) == 3)
        ) Thread.sleep(50)
        assertEquals(3, count("customer_audit", id)); assertEquals(3, count("customer_notifications", id))
        assertEquals(
            3,
            db.queryForObject(
                "SELECT count(*) FROM customer_outbox WHERE customer_id=? AND status='PUBLISHED'",
                Int::class.java,
                id
            )
        )
        client.mutateWith(auth).get().uri("/api/notifications").exchange().expectStatus().isOk
            .expectBody().jsonPath("$.length()").isEqualTo(3)
    }

    private fun count(table: String, id: UUID) =
        db.queryForObject("SELECT count(*) FROM $table WHERE customer_id=?", Int::class.java, id) ?: 0
}
