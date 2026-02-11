package com.kinetix.risk

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*

class HealthRouteTest : FunSpec({

    test("GET /health returns 200 OK") {
        testApplication {
            application { module() }
            val response = client.get("/health")
            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("GET /health returns status UP") {
        testApplication {
            application { module() }
            val response = client.get("/health")
            response.bodyAsText() shouldBe """{"status":"UP"}"""
        }
    }
})
