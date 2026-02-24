package com.kinetix.correlation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class HealthRouteTest : FunSpec({

    test("health endpoint returns 200 with UP status") {
        testApplication {
            application { module() }
            val response = client.get("/health")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "UP"
        }
    }
})
