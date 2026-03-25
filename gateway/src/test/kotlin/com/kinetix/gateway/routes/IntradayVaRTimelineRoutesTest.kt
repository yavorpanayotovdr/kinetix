package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.*

private val sampleTimeline = buildJsonObject {
    put("bookId", "book-1")
    putJsonArray("varPoints") {
        addJsonObject {
            put("timestamp", "2026-03-25T09:00:00Z")
            put("varValue", 12500.0)
            put("expectedShortfall", 15000.0)
            put("delta", 0.65)
            put("gamma", JsonNull)
            put("vega", JsonNull)
        }
    }
    putJsonArray("tradeAnnotations") {
        addJsonObject {
            put("timestamp", "2026-03-25T09:15:00Z")
            put("instrumentId", "AAPL")
            put("side", "BUY")
            put("quantity", "100")
            put("tradeId", "T001")
        }
    }
}

class IntradayVaRTimelineRoutesTest : FunSpec({

    val riskClient = mockk<RiskServiceClient>()

    beforeEach {
        clearMocks(riskClient)
    }

    test("GET /api/v1/risk/var/{bookId}/intraday returns 200 with timeline") {
        coEvery {
            riskClient.getIntradayVaRTimeline("book-1", "2026-03-25T08:00:00Z", "2026-03-25T17:00:00Z")
        } returns sampleTimeline

        testApplication {
            install(ContentNegotiation) { json() }
            routing { intradayVaRTimelineProxyRoutes(riskClient) }

            val response = client.get(
                "/api/v1/risk/var/book-1/intraday" +
                    "?from=2026-03-25T08:00:00Z&to=2026-03-25T17:00:00Z",
            )
            response.status shouldBe HttpStatusCode.OK

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["bookId"]?.jsonPrimitive?.content shouldBe "book-1"
            body["varPoints"]?.jsonArray?.size shouldBe 1
            body["tradeAnnotations"]?.jsonArray?.size shouldBe 1
        }
    }

    test("GET /api/v1/risk/var/{bookId}/intraday returns 400 when from is missing") {
        testApplication {
            install(ContentNegotiation) { json() }
            routing { intradayVaRTimelineProxyRoutes(riskClient) }

            val response = client.get(
                "/api/v1/risk/var/book-1/intraday?to=2026-03-25T17:00:00Z",
            )
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /api/v1/risk/var/{bookId}/intraday returns 400 when to is missing") {
        testApplication {
            install(ContentNegotiation) { json() }
            routing { intradayVaRTimelineProxyRoutes(riskClient) }

            val response = client.get(
                "/api/v1/risk/var/book-1/intraday?from=2026-03-25T08:00:00Z",
            )
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /api/v1/risk/var/{bookId}/intraday returns 404 when upstream returns null") {
        coEvery {
            riskClient.getIntradayVaRTimeline(any(), any(), any())
        } returns null

        testApplication {
            install(ContentNegotiation) { json() }
            routing { intradayVaRTimelineProxyRoutes(riskClient) }

            val response = client.get(
                "/api/v1/risk/var/unknown-book/intraday" +
                    "?from=2026-03-25T08:00:00Z&to=2026-03-25T17:00:00Z",
            )
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/risk/var/{bookId}/intraday passes bookId, from and to to the risk client") {
        coEvery {
            riskClient.getIntradayVaRTimeline("book-1", "2026-03-25T09:00:00Z", "2026-03-25T10:00:00Z")
        } returns sampleTimeline

        testApplication {
            install(ContentNegotiation) { json() }
            routing { intradayVaRTimelineProxyRoutes(riskClient) }

            client.get(
                "/api/v1/risk/var/book-1/intraday" +
                    "?from=2026-03-25T09:00:00Z&to=2026-03-25T10:00:00Z",
            )

            coVerify(exactly = 1) {
                riskClient.getIntradayVaRTimeline("book-1", "2026-03-25T09:00:00Z", "2026-03-25T10:00:00Z")
            }
        }
    }
})
