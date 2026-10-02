package com.kotlin.template.shared.infrastructure.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info().title("Template API").version("v1")
                .description("API de autenticação, usuários, customers e notificações. Customers pertencem ao usuário autenticado; alterações geram eventos assíncronos para auditoria e notificações. As rotas de recuperação de entrega exigem ADMIN. Cadastre-se em /api/auth/signup, faça login em /api/auth/login e use o accessToken no botão Authorize. Senhas e hashes nunca são retornados. Roles são atribuídas pelo servidor.")
        )
        .components(
            Components().addSecuritySchemes(
                "bearerAuth", SecurityScheme()
                    .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                    .description("JWT recebido no login. Informe somente o accessToken; o Swagger adiciona Bearer ao header Authorization.")
            )
        )
        .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
}
