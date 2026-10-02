package com.kotlin.template

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
    fromApplication<TemplateApplication>().with(TestcontainersConfiguration::class).run(*args)
}
