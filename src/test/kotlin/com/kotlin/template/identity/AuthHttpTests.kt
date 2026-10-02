package com.kotlin.template.identity

import com.kotlin.template.identity.application.usecase.currentuser.CurrentUser
import com.kotlin.template.identity.application.usecase.login.Login
import com.kotlin.template.identity.application.usecase.signup.Signup
import com.kotlin.template.identity.domain.model.User
import com.kotlin.template.identity.domain.repository.UserRepository
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
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import tools.jackson.databind.ObjectMapper

@WebMvcTest(
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
    lateinit var mvc: MockMvc
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
        val created = mvc.perform(
            post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":" Maria ","email":"MARIA@example.com","password":"valid-password"}""")
        )
            .andExpect(status().isCreated).andExpect(jsonPath("$.name").value("Maria"))
            .andExpect(jsonPath("$.email").value("maria@example.com"))
            .andExpect(jsonPath("$.roles[0]").value("USER"))
            .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andReturn().response.contentAsString
        val token = login("maria@example.com", "valid-password")
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk).andExpect(content().json(created))
        kotlin.test.assertNotEquals("valid-password", users.values.values.single().passwordHash)
    }

    @Test
    fun `long multibyte passwords are accepted without truncation`() {
        val password = "ç".repeat(100) + "original"
        mvc.perform(
            post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf(
                            "name" to "Maria",
                            "email" to "maria@example.com",
                            "password" to password
                        )
                    )
                )
        )
            .andExpect(status().isCreated)
        login("maria@example.com", password)
        mvc.perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf(
                            "email" to "maria@example.com",
                            "password" to "ç".repeat(100) + "modified"
                        )
                    )
                )
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `duplicate email is case insensitive`() {
        register()
        mvc.perform(
            post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Another","email":"MARIA@example.com","password":"valid-password"}""")
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `validation does not expose rejected password`() {
        mvc.perform(
            post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":" ","email":"invalid","password":"short"}""")
        )
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.errors").isArray)
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("short"))))
    }

    @Test
    fun `roles cannot be assigned through signup`() {
        mvc.perform(
            post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Maria","email":"maria@example.com","password":"valid-password","roles":["ADMIN"]}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `invalid and unknown credentials return same error`() {
        register()
        for (email in listOf("maria@example.com", "missing@example.com")) {
            mvc.perform(
                post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"$email","password":"incorrect-password"}""")
            )
                .andExpect(status().isUnauthorized).andExpect(jsonPath("$.detail").value("Email ou senha inválidos."))
        }
    }

    @Test
    fun `missing tampered expired and wrong issuer tokens are rejected`() {
        register()
        val token = login("maria@example.com", "valid-password")
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized)
        val now = Instant.now()
        val subject = users.values.values.single().id.toString()
        val expired = encode(subject, "test-template", now.minusSeconds(3600), now.minusSeconds(600))
        val wrongIssuer = encode(subject, "another-issuer", now, now.plusSeconds(600))
        for (invalid in listOf(token.dropLast(8) + "tampered", expired, wrongIssuer)) {
            mvc.perform(get("/api/users/me").header("Authorization", "Bearer $invalid"))
                .andExpect(status().isUnauthorized)
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        }
    }

    @Test
    fun `user role cannot access administrative actuator endpoint`() {
        register()
        mvc.perform(
            get("/actuator/info").header(
                "Authorization",
                "Bearer ${login("maria@example.com", "valid-password")}"
            )
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `cors only permits configured origins`() {
        mvc.perform(
            options("/api/auth/login").header("Origin", "https://trusted.example")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type")
        )
            .andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Allow-Origin", "https://trusted.example"))
        mvc.perform(
            options("/api/auth/login").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")
        ).andExpect(status().isForbidden)
    }

    private fun register() {
        mvc.perform(
            post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Maria","email":"maria@example.com","password":"valid-password"}""")
        )
            .andExpect(status().isCreated)
    }

    private fun login(email: String, password: String): String {
        val response = mvc.perform(
            post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("email" to email, "password" to password)))
        )
            .andExpect(status().isOk).andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900)).andReturn().response.contentAsString
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
        override fun findByEmail(email: String) = values.values.firstOrNull { it.email == email }
        override fun findById(id: UUID) = values[id]
        override fun create(user: User): User {
            values[user.id] = user; return user
        }
    }
}
