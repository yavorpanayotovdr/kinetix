package com.kinetix.gateway.routes

import com.kinetix.common.model.*
import com.kinetix.gateway.client.MarketDataServiceClient
import com.kinetix.gateway.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")
private fun usd(amount: String) = Money(BigDecimal(amount), USD)

class MarketDataRoutesTest : FunSpec({

    val marketDataClient = mockk<MarketDataServiceClient>()

    beforeEach { clearMocks(marketDataClient) }

    // --- GET /api/v1/market-data/{instrumentId}/latest ---

    test("GET /latest returns 200 with latest price") {
        val point = MarketDataPoint(
            instrumentId = InstrumentId("AAPL"),
            price = usd("170.50"),
            timestamp = Instant.parse("2025-06-15T14:30:00Z"),
            source = MarketDataSource.BLOOMBERG,
        )
        coEvery { marketDataClient.getLatestPrice(InstrumentId("AAPL")) } returns point

        testApplication {
            application { module(marketDataClient) }
            val response = client.get("/api/v1/market-data/AAPL/latest")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["instrumentId"]?.jsonPrimitive?.content shouldBe "AAPL"
            body["price"]!!.jsonObject["amount"]?.jsonPrimitive?.content shouldBe "170.50"
            body["price"]!!.jsonObject["currency"]?.jsonPrimitive?.content shouldBe "USD"
            body["timestamp"]?.jsonPrimitive?.content shouldBe "2025-06-15T14:30:00Z"
            body["source"]?.jsonPrimitive?.content shouldBe "BLOOMBERG"
        }
    }

    test("GET /latest returns 404 when no price available") {
        coEvery { marketDataClient.getLatestPrice(InstrumentId("UNKNOWN")) } returns null

        testApplication {
            application { module(marketDataClient) }
            val response = client.get("/api/v1/market-data/UNKNOWN/latest")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /latest passes correct instrumentId to client") {
        val idSlot = slot<InstrumentId>()
        coEvery { marketDataClient.getLatestPrice(capture(idSlot)) } returns MarketDataPoint(
            instrumentId = InstrumentId("MSFT"),
            price = usd("400.00"),
            timestamp = Instant.parse("2025-06-15T14:30:00Z"),
            source = MarketDataSource.REUTERS,
        )

        testApplication {
            application { module(marketDataClient) }
            client.get("/api/v1/market-data/MSFT/latest")
            idSlot.captured shouldBe InstrumentId("MSFT")
        }
    }

    test("GET /latest returns 400 for blank instrumentId") {
        testApplication {
            application { module(marketDataClient) }
            val response = client.get("/api/v1/market-data/%20/latest")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    // --- GET /api/v1/market-data/{instrumentId}/history ---

    test("GET /history returns 200 with price list") {
        val points = listOf(
            MarketDataPoint(
                instrumentId = InstrumentId("AAPL"),
                price = usd("170.00"),
                timestamp = Instant.parse("2025-06-15T10:00:00Z"),
                source = MarketDataSource.BLOOMBERG,
            ),
            MarketDataPoint(
                instrumentId = InstrumentId("AAPL"),
                price = usd("171.00"),
                timestamp = Instant.parse("2025-06-15T11:00:00Z"),
                source = MarketDataSource.BLOOMBERG,
            ),
        )
        coEvery {
            marketDataClient.getPriceHistory(
                InstrumentId("AAPL"),
                Instant.parse("2025-06-15T10:00:00Z"),
                Instant.parse("2025-06-15T12:00:00Z"),
            )
        } returns points

        testApplication {
            application { module(marketDataClient) }
            val response = client.get("/api/v1/market-data/AAPL/history?from=2025-06-15T10:00:00Z&to=2025-06-15T12:00:00Z")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 2
            body[0].jsonObject["price"]!!.jsonObject["amount"]?.jsonPrimitive?.content shouldBe "170.00"
            body[1].jsonObject["price"]!!.jsonObject["amount"]?.jsonPrimitive?.content shouldBe "171.00"
        }
    }

    test("GET /history returns empty list when no data") {
        coEvery {
            marketDataClient.getPriceHistory(
                InstrumentId("AAPL"),
                Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2020-01-02T00:00:00Z"),
            )
        } returns emptyList()

        testApplication {
            application { module(marketDataClient) }
            val response = client.get("/api/v1/market-data/AAPL/history?from=2020-01-01T00:00:00Z&to=2020-01-02T00:00:00Z")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "[]"
        }
    }

    test("GET /history returns 400 when from param is missing") {
        testApplication {
            application { module(marketDataClient) }
            val response = client.get("/api/v1/market-data/AAPL/history?to=2025-06-15T12:00:00Z")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /history returns 400 when to param is missing") {
        testApplication {
            application { module(marketDataClient) }
            val response = client.get("/api/v1/market-data/AAPL/history?from=2025-06-15T10:00:00Z")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
