package com.kotlin.template.support

import java.util.concurrent.atomic.AtomicInteger
import reactor.core.publisher.Mono
import reactor.netty.http.server.HttpServer

/** Local provider fixture: integration tests never depend on the public demo URL. */
class CustomerVerificationHttpStub : AutoCloseable {
    @Volatile
    var status = "VERIFIED"
    val requests = AtomicInteger()
    private val server = HttpServer.create().host("127.0.0.1").port(0).route { routes ->
        routes.get("/verification") { _, response ->
            requests.incrementAndGet()
            response.header("Content-Type", "application/json").sendString(Mono.just("""
                {
                    "verificationId": "ver_8f21d72c",
                    "requestId": "0199a4f7-6c28-7c31-bb59-477995177aba",
                    "status": "$status",
                    "email": {
                        "address": "john.doe@example.com",
                        "valid": false,
                        "deliverable": false,
                        "disposable": true
                    },
                    "risk": {"level": "HIGH", "score": 100},
                    "verifiedAt": "2026-10-02T15:20:31Z"
                }
            """.trimIndent()))
        }
    }.bindNow()

    val url: String get() = "http://127.0.0.1:${server.port()}/verification"

    override fun close() = server.disposeNow()
}
