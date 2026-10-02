package com.kotlin.template

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "app.security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "spring.datasource.password=test",
        "spring.docker.compose.enabled=false",
    ]
)
class TemplateApplicationTests {

    @Test
    fun contextLoads() {
    }

}
