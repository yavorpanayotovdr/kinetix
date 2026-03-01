package com.kinetix.gateway.routes

import com.kinetix.gateway.client.PositionServiceClient
import com.kinetix.gateway.client.PriceServiceClient
import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.moduleWithDataQuality
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*

class DataQualityRoutesTest : FunSpec({

    val positionClient = mockk<PositionServiceClient>()
    val priceClient = mockk<PriceServiceClient>()
    val riskClient = mockk<RiskServiceClient>()

    beforeEach { clearMocks(positionClient, priceClient, riskClient) }

    test("GET /api/v1/data-quality/status returns 200 with quality checks") {
        testApplication {
            application { moduleWithDataQuality(positionClient, priceClient, riskClient) }
            val response = client.get("/api/v1/data-quality/status")
            response.status shouldBe HttpStatusCode.OK

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body.containsKey("overall") shouldBe true
            body.containsKey("checks") shouldBe true

            val checks = body["checks"]?.jsonArray
            checks!!.size shouldBe 3
        }
    }

    test("GET /api/v1/data-quality/status returns check names for price freshness, position count, and risk completeness") {
        testApplication {
            application { moduleWithDataQuality(positionClient, priceClient, riskClient) }
            val response = client.get("/api/v1/data-quality/status")
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val checks = body["checks"]!!.jsonArray

            val checkNames = checks.map { it.jsonObject["name"]?.jsonPrimitive?.content }
            checkNames shouldBe listOf("Price Freshness", "Position Count", "Risk Result Completeness")
        }
    }

    test("GET /api/v1/data-quality/status returns OK overall when all checks pass") {
        testApplication {
            application { moduleWithDataQuality(positionClient, priceClient, riskClient) }
            val response = client.get("/api/v1/data-quality/status")
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["overall"]?.jsonPrimitive?.content shouldBe "OK"
        }
    }

    test("each check has required fields: name, status, message, lastChecked") {
        testApplication {
            application { moduleWithDataQuality(positionClient, priceClient, riskClient) }
            val response = client.get("/api/v1/data-quality/status")
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val checks = body["checks"]!!.jsonArray

            for (check in checks) {
                val obj = check.jsonObject
                obj.containsKey("name") shouldBe true
                obj.containsKey("status") shouldBe true
                obj.containsKey("message") shouldBe true
                obj.containsKey("lastChecked") shouldBe true
            }
        }
    }
})
