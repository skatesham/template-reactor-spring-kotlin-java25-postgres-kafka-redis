package com.kotlin.template.identity

import com.kotlin.template.identity.domain.model.Role
import com.kotlin.template.identity.domain.model.User
import com.kotlin.template.identity.infrastructure.security.JwtProperties
import com.kotlin.template.identity.infrastructure.security.SecurityConfig
import java.time.Duration
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class IdentityDomainAndSecurityTests {
    @Test
    fun `registration normalizes names and email while retaining protected credential hash`() {
        val user = User.register(" Synthetic Owner ", " SYNTHETIC@EXAMPLE.COM ", "protected-hash")
        assertEquals("Synthetic Owner", user.name)
        assertEquals("synthetic@example.com", user.email)
        assertEquals("protected-hash", user.passwordHash)
        assertEquals(setOf(Role.USER), user.roles)
    }

    @Test
    fun `domain rejects invalid names email credential hash and empty roles`() {
        val id = UUID.randomUUID()
        for (name in listOf("", " ", "n".repeat(101))) {
            assertFailsWith<IllegalArgumentException> {
                User(
                    id,
                    name,
                    "synthetic@example.com",
                    "hash",
                    setOf(Role.USER)
                )
            }
        }
        for (email in listOf("", " ", "EMAIL@EXAMPLE.COM", "a".repeat(255))) {
            assertFailsWith<IllegalArgumentException> { User(id, "Synthetic", email, "hash", setOf(Role.USER)) }
        }
        assertFailsWith<IllegalArgumentException> {
            User(
                id,
                "Synthetic",
                "synthetic@example.com",
                " ",
                setOf(Role.USER)
            )
        }
        assertFailsWith<IllegalArgumentException> { User(id, "Synthetic", "synthetic@example.com", "hash", emptySet()) }
    }

    @Test
    fun `roles are a defensive snapshot of caller input`() {
        val roles = mutableSetOf(Role.USER)
        val user = User(UUID.randomUUID(), "Synthetic", "synthetic@example.com", "hash", roles)
        roles.add(Role.ADMIN)
        assertEquals(setOf(Role.USER), user.roles)
    }

    @Test
    fun `JWT configuration rejects weak malformed keys and invalid token lifetimes`() {
        val config = SecurityConfig()
        assertFailsWith<IllegalArgumentException> { config.jwtSecretKey(JwtProperties("not-base64!", "test")) }
        assertFailsWith<IllegalArgumentException> {
            config.jwtSecretKey(
                JwtProperties(
                    Base64.getEncoder().encodeToString(ByteArray(31)), "test"
                )
            )
        }
        val key = config.jwtSecretKey(JwtProperties(Base64.getEncoder().encodeToString(ByteArray(32)), "test"))
        assertEquals("HmacSHA256", key.algorithm); assertEquals(32, key.encoded.size)
        for (ttl in listOf(Duration.ZERO, Duration.ofMillis(999), Duration.ofSeconds(-1))) {
            assertFailsWith<IllegalArgumentException> { JwtProperties("synthetic", "test", ttl) }
        }
        assertEquals(Duration.ofSeconds(1), JwtProperties("synthetic", "test", Duration.ofSeconds(1)).accessTokenTtl)
    }
}
