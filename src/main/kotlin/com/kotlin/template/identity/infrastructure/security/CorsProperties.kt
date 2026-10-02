package com.kotlin.template.identity.infrastructure.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.security.cors")
class CorsProperties(val allowedOrigins: List<String> = emptyList())
