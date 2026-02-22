package com.kinetix.acceptance

import com.kinetix.common.model.*
import com.kinetix.gateway.client.MarketDataServiceClient
import com.kinetix.gateway.module
import io.kotest.core.spec.style.BehaviorSpec
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

class GatewayMarketDataContractIntegrationTest : BehaviorSpec({

    val marketDataClient = mockk<MarketDataServiceClient>()

    beforeEach { clearMocks(marketDataClient) }

    given("gateway routing to market-data-service") {

        `when`("GET /api/v1/market-data/{instrumentId}/latest") {
            then("returns 200 with market data point shape") {
                coEvery { marketDataClient.getLatestPrice(InstrumentId("AAPL")) } returns MarketDataPoint(
                    instrumentId = InstrumentId("AAPL"),
                    price = Money(BigDecimal("150.00"), Currency.getInstance("USD")),
                    timestamp = Instant.parse("2025-01-15T10:00:00Z"),
                    source = MarketDataSource.EXCHANGE,
                )

                testApplication {
                    application { module(marketDataClient) }
                    val response = client.get("/api/v1/market-data/AAPL/latest")
                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body.containsKey("instrumentId") shouldBe true
                    body.containsKey("price") shouldBe true
                    body.containsKey("timestamp") shouldBe true
                    body.containsKey("source") shouldBe true
                    body["instrumentId"]?.jsonPrimitive?.content shouldBe "AAPL"
                }
            }
        }

        `when`("GET /api/v1/market-data/{instrumentId}/latest with no data") {
            then("returns 404") {
                coEvery { marketDataClient.getLatestPrice(InstrumentId("UNKNOWN")) } returns null

                testApplication {
                    application { module(marketDataClient) }
                    val response = client.get("/api/v1/market-data/UNKNOWN/latest")
                    response.status shouldBe HttpStatusCode.NotFound
                }
            }
        }

        `when`("GET /api/v1/market-data/{instrumentId}/history without required params") {
            then("returns 400") {
                testApplication {
                    application { module(marketDataClient) }
                    val response = client.get("/api/v1/market-data/AAPL/history")
                    response.status shouldBe HttpStatusCode.BadRequest
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body.containsKey("error") shouldBe true
                    body.containsKey("message") shouldBe true
                }
            }
        }
    }
})
