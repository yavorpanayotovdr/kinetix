package com.kinetix.gateway.routes

import com.kinetix.common.model.*
import com.kinetix.gateway.client.PriceServiceClient
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

class PriceRoutesTest : FunSpec({

    val priceClient = mockk<PriceServiceClient>()

    beforeEach { clearMocks(priceClient) }

    // --- GET /api/v1/prices/{instrumentId}/latest ---

    test("GET /latest returns 200 with latest price") {
        val point = PricePoint(
            instrumentId = InstrumentId("AAPL"),
            price = usd("170.50"),
            timestamp = Instant.parse("2025-06-15T14:30:00Z"),
            source = PriceSource.BLOOMBERG,
        )
        coEvery { priceClient.getLatestPrice(InstrumentId("AAPL")) } returns point

        testApplication {
            application { module(priceClient) }
            val response = client.get("/api/v1/prices/AAPL/latest")
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
        coEvery { priceClient.getLatestPrice(InstrumentId("UNKNOWN")) } returns null

        testApplication {
            application { module(priceClient) }
            val response = client.get("/api/v1/prices/UNKNOWN/latest")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /latest passes correct instrumentId to client") {
        val idSlot = slot<InstrumentId>()
        coEvery { priceClient.getLatestPrice(capture(idSlot)) } returns PricePoint(
            instrumentId = InstrumentId("MSFT"),
            price = usd("400.00"),
            timestamp = Instant.parse("2025-06-15T14:30:00Z"),
            source = PriceSource.REUTERS,
        )

        testApplication {
            application { module(priceClient) }
            client.get("/api/v1/prices/MSFT/latest")
            idSlot.captured shouldBe InstrumentId("MSFT")
        }
    }

    test("GET /latest returns 400 for blank instrumentId") {
        testApplication {
            application { module(priceClient) }
            val response = client.get("/api/v1/prices/%20/latest")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    // --- GET /api/v1/prices/{instrumentId}/history ---

    test("GET /history returns 200 with price list") {
        val points = listOf(
            PricePoint(
                instrumentId = InstrumentId("AAPL"),
                price = usd("170.00"),
                timestamp = Instant.parse("2025-06-15T10:00:00Z"),
                source = PriceSource.BLOOMBERG,
            ),
            PricePoint(
                instrumentId = InstrumentId("AAPL"),
                price = usd("171.00"),
                timestamp = Instant.parse("2025-06-15T11:00:00Z"),
                source = PriceSource.BLOOMBERG,
            ),
        )
        coEvery {
            priceClient.getPriceHistory(
                InstrumentId("AAPL"),
                Instant.parse("2025-06-15T10:00:00Z"),
                Instant.parse("2025-06-15T12:00:00Z"),
            )
        } returns points

        testApplication {
            application { module(priceClient) }
            val response = client.get("/api/v1/prices/AAPL/history?from=2025-06-15T10:00:00Z&to=2025-06-15T12:00:00Z")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 2
            body[0].jsonObject["price"]!!.jsonObject["amount"]?.jsonPrimitive?.content shouldBe "170.00"
            body[1].jsonObject["price"]!!.jsonObject["amount"]?.jsonPrimitive?.content shouldBe "171.00"
        }
    }

    test("GET /history returns empty list when no data") {
        coEvery {
            priceClient.getPriceHistory(
                InstrumentId("AAPL"),
                Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2020-01-02T00:00:00Z"),
            )
        } returns emptyList()

        testApplication {
            application { module(priceClient) }
            val response = client.get("/api/v1/prices/AAPL/history?from=2020-01-01T00:00:00Z&to=2020-01-02T00:00:00Z")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "[]"
        }
    }

    test("GET /history returns 400 when from param is missing") {
        testApplication {
            application { module(priceClient) }
            val response = client.get("/api/v1/prices/AAPL/history?to=2025-06-15T12:00:00Z")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /history returns 400 when to param is missing") {
        testApplication {
            application { module(priceClient) }
            val response = client.get("/api/v1/prices/AAPL/history?from=2025-06-15T10:00:00Z")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
