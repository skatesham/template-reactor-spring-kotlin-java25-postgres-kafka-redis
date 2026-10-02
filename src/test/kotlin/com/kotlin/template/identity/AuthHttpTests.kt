package com.kotlin.template.identity

import com.kotlin.template.identity.application.port.UserRepository
import com.kotlin.template.identity.application.usecase.currentuser.CurrentUser
import com.kotlin.template.identity.application.usecase.login.Login
import com.kotlin.template.identity.application.usecase.signup.Signup
import com.kotlin.template.identity.domain.model.User
import com.kotlin.template.identity.infrastructure.security.JwtAccessTokenIssuer
import com.kotlin.template.identity.infrastructure.security.SecurityConfig
import com.kotlin.template.identity.infrastructure.security.SpringPasswordHasher
import com.kotlin.template.identity.interfaces.rest.AuthController
import com.kotlin.template.identity.interfaces.rest.IdentityExceptionHandler
import com.kotlin.template.identity.interfaces.rest.UserController
import java.time.Instant
import java.util.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

@WebFluxTest(
    controllers = [AuthController::class, UserController::class], properties = [
        "app.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.security.jwt.issuer=test-template",
        "app.security.cors.allowed-origins=https://trusted.example",
        "spring.jackson.deserialization.fail-on-unknown-properties=true",
    ]
)
@Import(
    SecurityConfig::class, SpringPasswordHasher::class, JwtAccessTokenIssuer::class,
    Signup::class, Login::class, CurrentUser::class, IdentityExceptionHandler::class, AuthHttpTests.Fakes::class
)
class AuthHttpTests {
    @Autowired
    lateinit var client: WebTestClient
    @Autowired
    lateinit var mapper: ObjectMapper
    @Autowired
    lateinit var users: MemoryUsers
    @Autowired
    lateinit var encoder: JwtEncoder

    @BeforeEach
    fun reset() {
        users.values.clear()
    }

    @Test
    fun `signup login and bearer access never expose password`() {
        val created = client.post().uri("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"name":" Maria ","email":"MARIA@example.com","password":"valid-password"}""").exchange()
            .expectStatus().isCreated.expectBody().jsonPath("$.name").isEqualTo("Maria")
            .jsonPath("$.email").isEqualTo("maria@example.com")
            .jsonPath("$.roles[0]").isEqualTo("USER")
            .jsonPath("$.password").doesNotExist().jsonPath("$.passwordHash").doesNotExist()
            .returnResult().responseBody!!.toString(Charsets.UTF_8)
        val token = login("maria@example.com", "valid-password")
        client.get().uri("/api/users/me").header("Authorization", "Bearer $token").exchange()
            .expectStatus().isOk.expectBody().json(created)
        kotlin.test.assertNotEquals("valid-password", users.values.values.single().passwordHash)
    }

    @Test
    fun `long multibyte passwords are accepted without truncation`() {
        val password = "ç".repeat(100) + "original"
        client.post().uri("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapper.writeValueAsString(
                        mapOf(
                            "name" to "Maria",
                            "email" to "maria@example.com",
                            "password" to password
                        )
                    )
                ).exchange()
            .expectStatus().isCreated
        login("maria@example.com", password)
        client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    mapper.writeValueAsString(
                        mapOf(
                            "email" to "maria@example.com",
                            "password" to "ç".repeat(100) + "modified"
                        )
                    )
                ).exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `duplicate email is case insensitive`() {
        register()
        client.post().uri("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"name":"Another","email":"MARIA@example.com","password":"valid-password"}""").exchange()
            .expectStatus().isEqualTo(409)
    }

    @Test
    fun `validation does not expose rejected password`() {
        client.post().uri("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"name":" ","email":"invalid","password":"short"}""").exchange()
            .expectStatus().isBadRequest.expectBody().jsonPath("$.errors").isArray
            .consumeWith { org.hamcrest.MatcherAssert.assertThat(String(it.responseBody!!), org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("short"))) }
    }

    @Test
    fun `roles cannot be assigned through signup`() {
        client.post().uri("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"name":"Maria","email":"maria@example.com","password":"valid-password","roles":["ADMIN"]}""").exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `invalid and unknown credentials return same error`() {
        register()
        for (email in listOf("maria@example.com", "missing@example.com")) {
            client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"email":"$email","password":"incorrect-password"}""").exchange()
                .expectStatus().isUnauthorized.expectBody().jsonPath("$.detail").isEqualTo("Email ou senha inválidos.")
        }
    }

    @Test
    fun `missing tampered expired and wrong issuer tokens are rejected`() {
        register()
        val token = login("maria@example.com", "valid-password")
        client.get().uri("/api/users/me").exchange().expectStatus().isUnauthorized
        val now = Instant.now()
        val subject = users.values.values.single().id.toString()
        val expired = encode(subject, "test-template", now.minusSeconds(3600), now.minusSeconds(600))
        val wrongIssuer = encode(subject, "another-issuer", now, now.plusSeconds(600))
        for (invalid in listOf(token.dropLast(8) + "tampered", expired, wrongIssuer)) {
            client.get().uri("/api/users/me").header("Authorization", "Bearer $invalid").exchange()
                .expectStatus().isUnauthorized
                .expectHeader().contentTypeCompatibleWith("application/problem+json")
        }
    }

    @Test
    fun `user role cannot access administrative actuator endpoint`() {
        register()
        client.get().uri("/actuator/info").header(
                "Authorization",
                "Bearer ${login("maria@example.com", "valid-password")}"
            ).exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `cors only permits configured origins`() {
        client.options().uri("http://localhost/api/auth/login").header("Origin", "https://trusted.example")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type").exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("Access-Control-Allow-Origin", "https://trusted.example")
        client.options().uri("http://localhost/api/auth/login").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST").exchange().expectStatus().isForbidden
    }

    private fun register() {
        client.post().uri("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"name":"Maria","email":"maria@example.com","password":"valid-password"}""").exchange()
            .expectStatus().isCreated
    }

    private fun login(email: String, password: String): String {
        val response = client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapper.writeValueAsString(mapOf("email" to email, "password" to password))).exchange()
            .expectStatus().isOk.expectBody().jsonPath("$.tokenType").isEqualTo("Bearer")
            .jsonPath("$.expiresIn").isEqualTo(900).returnResult().responseBody!!.toString(Charsets.UTF_8)
        return mapper.readTree(response).get("accessToken").asString()
    }

    private fun encode(subject: String, issuer: String, issuedAt: Instant, expiresAt: Instant): String =
        encoder.encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer(issuer).subject(subject).issuedAt(issuedAt).expiresAt(expiresAt)
                    .claim("roles", listOf("USER")).build()
            )
        ).tokenValue

    @TestConfiguration(proxyBeanMethods = false)
    class Fakes {
        @Bean
        fun userRepository() = MemoryUsers()
    }

    class MemoryUsers : UserRepository {
        val values = mutableMapOf<UUID, User>()
        override fun findByEmail(email: String): Mono<User> = Mono.defer { Mono.justOrEmpty(values.values.firstOrNull { it.email == email }) }
        override fun findById(id: UUID): Mono<User> = Mono.defer { Mono.justOrEmpty(values[id]) }
        override fun create(user: User): Mono<User> = Mono.fromCallable { values[user.id] = user; user }
    }
}
