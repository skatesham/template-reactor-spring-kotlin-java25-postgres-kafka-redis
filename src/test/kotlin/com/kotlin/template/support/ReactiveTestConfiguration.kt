package com.kotlin.template.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.r2dbc.core.DatabaseClient

@TestConfiguration(proxyBeanMethods = false)
class ReactiveTestConfiguration {

    @Bean
    fun testDatabase(db: DatabaseClient) = TestDatabase(db)
}
