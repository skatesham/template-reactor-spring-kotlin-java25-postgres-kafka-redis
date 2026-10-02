package com.kotlin.template.identity.infrastructure.security

import java.util.*
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.*
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Configuration(proxyBeanMethods = false)
@EnableReactiveMethodSecurity
@EnableConfigurationProperties(JwtProperties::class, CorsProperties::class)
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder =
        DelegatingPasswordEncoder("pbkdf2", mapOf("pbkdf2" to Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()))

    @Bean
    fun jwtSecretKey(properties: JwtProperties): SecretKey {
        val bytes = try {
            Base64.getDecoder().decode(properties.secret)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("JWT_SECRET must be Base64 encoded")
        }
        require(bytes.size >= 32) { "JWT_SECRET must contain at least 32 random bytes encoded in Base64" }
        return SecretKeySpec(bytes, "HmacSHA256")
    }

    @Bean
    fun jwtEncoder(key: SecretKey): JwtEncoder = NimbusJwtEncoder.withSecretKey(key).build()

    @Bean
    fun jwtDecoder(key: SecretKey, properties: JwtProperties): ReactiveJwtDecoder =
        NimbusReactiveJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().apply {
            setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer))
        }

    @Bean
    fun corsConfigurationSource(properties: CorsProperties): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = properties.allowedOrigins.filter { it.isNotBlank() }
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("Authorization", "Content-Type", "Accept", "Idempotency-Key")
            allowCredentials = false
            maxAge = 3600L
        }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
    }

    @Bean
    fun securityFilterChain(http: ServerHttpSecurity,
        @Qualifier("corsConfigurationSource") cors: CorsConfigurationSource): SecurityWebFilterChain {
        val unauthorized = ServerAuthenticationEntryPoint { exchange, _ ->
            exchange.response.headers.set("WWW-Authenticate", "Bearer")
            writeProblem(exchange, 401, "Unauthorized", "Autenticação necessária ou token inválido.")
        }
        val forbidden = ServerAccessDeniedHandler { exchange, _ ->
            writeProblem(exchange, 403, "Forbidden", "Acesso não permitido.")
        }
        val authorities = JwtGrantedAuthoritiesConverter().apply {
            setAuthoritiesClaimName("roles"); setAuthorityPrefix("ROLE_")
        }
        val converter = ReactiveJwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(ReactiveJwtGrantedAuthoritiesConverterAdapter(authorities))
        }
        return http.csrf { it.disable() }.cors { it.configurationSource(cors) }
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .requestCache { it.requestCache(NoOpServerRequestCache.getInstance()) }
            .httpBasic { it.disable() }.formLogin { it.disable() }.logout { it.disable() }
            .authorizeExchange {
                it.pathMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                    .pathMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html",
                        "/swagger-ui/**", "/actuator/health", "/actuator/health/**").permitAll()
                    .pathMatchers("/api/admin/**", "/actuator/**").hasRole("ADMIN")
                    .anyExchange().authenticated()
            }.exceptionHandling { it.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden) }
            .oauth2ResourceServer {
                it.jwt { jwt -> jwt.jwtAuthenticationConverter(converter) }
                    .authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden)
            }.build()
    }

    private fun writeProblem(exchange: ServerWebExchange, status: Int, title: String, detail: String): Mono<Void> {
        val response = exchange.response
        response.statusCode = org.springframework.http.HttpStatusCode.valueOf(status)
        response.headers.contentType = org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON
        val bytes = """{"type":"about:blank","title":"$title","status":$status,"detail":"$detail"}""".toByteArray(Charsets.UTF_8)
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)))
    }
}
