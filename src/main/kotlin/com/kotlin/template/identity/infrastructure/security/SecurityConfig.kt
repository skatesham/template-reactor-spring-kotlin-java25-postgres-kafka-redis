package com.kotlin.template.identity.infrastructure.security

import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletResponse
import java.util.*
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.*
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
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
    fun jwtDecoder(key: SecretKey, properties: JwtProperties): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().apply {
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
    fun securityFilterChain(
        http: HttpSecurity,
        @Qualifier("corsConfigurationSource") cors: CorsConfigurationSource,
    ): SecurityFilterChain {
        val unauthorized = AuthenticationEntryPoint { _, response, _ ->
            response.setHeader("WWW-Authenticate", "Bearer")
            writeProblem(response, 401, "Unauthorized", "Autenticação necessária ou token inválido.")
        }
        val forbidden = AccessDeniedHandler { _, response, _ ->
            writeProblem(response, 403, "Forbidden", "Acesso não permitido.")
        }
        val authorities = JwtGrantedAuthoritiesConverter().apply {
            setAuthoritiesClaimName("roles")
            setAuthorityPrefix("ROLE_")
        }
        val converter = JwtAuthenticationConverter().apply { setJwtGrantedAuthoritiesConverter(authorities) }
        return http
            // API stateless: autenticação somente pelo header Bearer, sem cookies de sessão.
            .csrf { it.disable() }
            .cors { it.configurationSource(cors) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .requestCache { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .authorizeHttpRequests {
                it.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/actuator/health",
                        "/actuator/health/**"
                    ).permitAll()
                    .requestMatchers("/actuator/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            }
            .exceptionHandling { it.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden) }
            .oauth2ResourceServer {
                it.jwt { jwt -> jwt.jwtAuthenticationConverter(converter) }
                    .authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden)
            }
            .build()
    }

    private fun writeProblem(response: HttpServletResponse, status: Int, title: String, detail: String) {
        response.status = status
        response.contentType = "application/problem+json"
        response.characterEncoding = "UTF-8"
        response.writer.write("""{"type":"about:blank","title":"$title","status":$status,"detail":"$detail"}""")
    }
}
