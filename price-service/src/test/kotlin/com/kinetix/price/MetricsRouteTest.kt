package com.kinetix.price

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*

class MetricsRouteTest : FunSpec({

    test("GET /metrics returns 200 OK") {
        testApplication {
            application { module() }
            val response = client.get("/metrics")
            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("GET /metrics returns Prometheus exposition format") {
        testApplication {
            application { module() }
            val response = client.get("/metrics")
            response.bodyAsText() shouldContain "# HELP"
        }
    }

    test("GET /metrics includes JVM metrics") {
        testApplication {
            application { module() }
            val response = client.get("/metrics")
            response.bodyAsText() shouldContain "jvm_memory"
        }
    }
})
