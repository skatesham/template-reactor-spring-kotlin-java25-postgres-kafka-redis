package com.kotlin.template.identity

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.ObjectMapper

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(
    properties = [
        "app.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "spring.datasource.password=test",
        "spring.docker.compose.enabled=false",
        "app.customer.messaging.enabled=false",
        "app.customer.jobs.enabled=false",
    ]
)
class IdentityIntegrationTests {
    @Autowired
    lateinit var mvc: MockMvc
    @Autowired
    lateinit var jdbc: JdbcTemplate
    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `migration persistence login and openapi work together`() {
        val email = "user-${UUID.randomUUID()}@example.com"
        mvc.perform(
            post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf(
                            "name" to "Synthetic User",
                            "email" to email,
                            "password" to "valid-password"
                        )
                    )
                )
        )
            .andExpect(status().isCreated).andExpect(jsonPath("$.roles[0]").value("USER"))
        assertNotEquals(
            "valid-password",
            jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String::class.java, email)
        )
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT count(*) FROM user_roles ur JOIN users u ON u.id = ur.user_id WHERE u.email = ? AND ur.role_name = 'USER'",
                Int::class.java,
                email
            )
        )
        val response = mvc.perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("email" to email.uppercase(), "password" to "valid-password")))
        )
            .andExpect(status().isOk).andReturn().response.contentAsString
        val token = mapper.readTree(response).get("accessToken").asString()
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk).andExpect(jsonPath("$.email").value(email))
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
            .andExpect(jsonPath("$.security[0].bearerAuth").isArray)
            .andExpect(jsonPath("$.paths['/api/auth/signup'].post.security").isEmpty)
            .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty)
            .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.password.writeOnly").value(true))
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk)
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
                    mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(payload))
                        .andReturn().response.status
                })
            }
            start.countDown()
            assertEquals(listOf(201, 409), results.map { it.get() }.sorted())
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Int::class.java, email))
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
    }
}
