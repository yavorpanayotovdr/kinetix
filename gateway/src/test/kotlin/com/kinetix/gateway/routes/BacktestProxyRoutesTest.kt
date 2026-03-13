package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RegulatoryServiceClient
import com.kinetix.gateway.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*

private val sampleBacktestComparison = buildJsonObject {
    put("baseId", "bt-001")
    put("targetId", "bt-002")
    put("baseExceptions", 3)
    put("targetExceptions", 5)
    put("verdict", "TARGET_WORSE")
}

class BacktestProxyRoutesTest : FunSpec({

    val regulatoryClient = mockk<RegulatoryServiceClient>()

    beforeEach { clearMocks(regulatoryClient) }

    test("GET /api/v1/regulatory/backtest/compare returns comparison result") {
        coEvery { regulatoryClient.compareBacktests("bt-001", "bt-002") } returns sampleBacktestComparison

        testApplication {
            application { module(regulatoryClient) }
            val response = client.get("/api/v1/regulatory/backtest/compare?baseId=bt-001&targetId=bt-002")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["baseId"]?.jsonPrimitive?.content shouldBe "bt-001"
            body["targetId"]?.jsonPrimitive?.content shouldBe "bt-002"
            body["verdict"]?.jsonPrimitive?.content shouldBe "TARGET_WORSE"
        }
    }

    test("GET /api/v1/regulatory/backtest/compare returns 404 when no data found") {
        coEvery { regulatoryClient.compareBacktests("bt-001", "bt-002") } returns null

        testApplication {
            application { module(regulatoryClient) }
            val response = client.get("/api/v1/regulatory/backtest/compare?baseId=bt-001&targetId=bt-002")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/regulatory/backtest/compare returns 400 when baseId is missing") {
        testApplication {
            application { module(regulatoryClient) }
            val response = client.get("/api/v1/regulatory/backtest/compare?targetId=bt-002")
            response.status shouldBe HttpStatusCode.BadRequest
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body.containsKey("error") shouldBe true
        }
    }

    test("GET /api/v1/regulatory/backtest/compare returns 400 when targetId is missing") {
        testApplication {
            application { module(regulatoryClient) }
            val response = client.get("/api/v1/regulatory/backtest/compare?baseId=bt-001")
            response.status shouldBe HttpStatusCode.BadRequest
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body.containsKey("error") shouldBe true
        }
    }

    test("GET /api/v1/regulatory/backtest/compare forwards both IDs to client") {
        coEvery { regulatoryClient.compareBacktests("bt-A", "bt-B") } returns sampleBacktestComparison

        testApplication {
            application { module(regulatoryClient) }
            client.get("/api/v1/regulatory/backtest/compare?baseId=bt-A&targetId=bt-B")
            coVerify { regulatoryClient.compareBacktests("bt-A", "bt-B") }
        }
    }

    test("existing stress scenario routes still work after backtest proxy is installed") {
        coEvery { regulatoryClient.listScenarios() } returns emptyList()

        testApplication {
            application { module(regulatoryClient) }
            val response = client.get("/api/v1/stress-scenarios")
            response.status shouldBe HttpStatusCode.OK
        }
    }
})
