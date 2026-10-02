package com.kotlin.template.identity.infrastructure.security

import com.kotlin.template.identity.application.result.AccessToken
import com.kotlin.template.identity.application.port.AccessTokenIssuer
import com.kotlin.template.identity.domain.model.User
import java.time.Instant
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component

@Component
class JwtAccessTokenIssuer(private val encoder: JwtEncoder, private val properties: JwtProperties) : AccessTokenIssuer {
    override fun issue(user: User): AccessToken {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder().issuer(properties.issuer).subject(user.id.toString())
            .issuedAt(now).expiresAt(now.plus(properties.accessTokenTtl))
            .claim("roles", user.roles.map { it.name }).build()
        val header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build()
        return AccessToken(
            encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue,
            properties.accessTokenTtl.seconds
        )
    }
}
