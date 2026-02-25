package com.kinetix.volatility

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*

class SwaggerUiTest : FunSpec({

    test("GET /openapi.json returns valid OpenAPI spec") {
        testApplication {
            application { module() }
            val response = client.get("/openapi.json")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "\"openapi\""
            body shouldContain "Volatility Service API"
        }
    }

    test("GET /swagger returns Swagger UI") {
        testApplication {
            application { module() }
            val response = client.get("/swagger")
            response.status shouldBe HttpStatusCode.OK
        }
    }
})
