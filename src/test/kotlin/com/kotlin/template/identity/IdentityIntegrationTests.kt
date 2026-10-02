package com.kotlin.template.identity

import com.kotlin.template.support.TestDatabase
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.ObjectMapper

@Testcontainers
@AutoConfigureWebTestClient
@org.springframework.context.annotation.Import(com.kotlin.template.support.ReactiveTestConfiguration::class)
@SpringBootTest(
    properties = [
        "app.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "spring.r2dbc.password=test",
        "spring.docker.compose.enabled=false",
        "app.customer.messaging.enabled=false",
        "app.customer.jobs.enabled=false",
    ]
)
class IdentityIntegrationTests {
    @Autowired
    lateinit var client: WebTestClient
    @Autowired
    lateinit var db: TestDatabase
    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `migration persistence login and openapi work together`() {
        val email = "user-${UUID.randomUUID()}@example.com"
        client.post().uri("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapper.writeValueAsString(
                        mapOf(
                            "name" to "Synthetic User",
                            "email" to email,
                            "password" to "valid-password"
                        )
                    )
                ).exchange()
            .expectStatus().isCreated.expectBody().jsonPath("$.roles[0]").isEqualTo("USER")
        assertNotEquals(
            "valid-password",
            db.queryForObject("SELECT password_hash FROM users WHERE email = ?", String::class.java, email)
        )
        assertEquals(
            1,
            db.queryForObject(
                "SELECT count(*) FROM user_roles ur JOIN users u ON u.id = ur.user_id WHERE u.email = ? AND ur.role_name = 'USER'",
                Int::class.java,
                email
            )
        )
        val response = client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapper.writeValueAsString(mapOf("email" to email.uppercase(), "password" to "valid-password"))).exchange()
            .expectStatus().isOk.expectBody().returnResult().responseBody!!.toString(Charsets.UTF_8)
        val token = mapper.readTree(response).get("accessToken").asString()
        client.get().uri("/api/users/me").header("Authorization", "Bearer $token").exchange()
            .expectStatus().isOk.expectBody().jsonPath("$.email").isEqualTo(email)
        client.get().uri("/v3/api-docs").exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.components.securitySchemes.bearerAuth.scheme").isEqualTo("bearer")
            .jsonPath("$.security[0].bearerAuth").isArray
            .jsonPath("$.paths['/api/auth/signup'].post.security").isEmpty
            .jsonPath("$.paths['/api/auth/login'].post.security").isEmpty
            .jsonPath("$.components.schemas.SignupRequest.properties.password.writeOnly").isEqualTo(true)
        client.get().uri("/swagger-ui/index.html").exchange().expectStatus().isOk
    }

    @Test
    fun `concurrent signup yields one created user and one conflict`() {
        val email = "race-${UUID.randomUUID()}@example.com"
        val payload = mapper.writeValueAsString(
            mapOf(
                "name" to "Synthetic User",
                "email" to email,
                "password" to "valid-password"
            )
        )
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val results = (1..2).map {
                executor.submit(Callable {
                    start.await()
                    client.post().uri("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).bodyValue(payload).exchange()
                        .expectBody().returnResult().status.value()
                })
            }
            start.countDown()
            assertEquals(listOf(201, 409), results.map { it.get() }.sorted())
        }
        assertEquals(1, db.queryForObject("SELECT count(*) FROM users WHERE email = ?", Int::class.java, email))
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
    }
}
