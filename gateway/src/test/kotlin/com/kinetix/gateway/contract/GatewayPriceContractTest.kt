package com.kinetix.gateway.contract

import com.kinetix.common.model.*
import com.kinetix.gateway.client.PriceServiceClient
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

class GatewayPriceContractTest : BehaviorSpec({

    val priceClient = mockk<PriceServiceClient>()

    beforeEach { clearMocks(priceClient) }

    given("gateway routing to price-service") {

        `when`("GET /api/v1/prices/{instrumentId}/latest") {
            then("returns 200 with price point shape") {
                coEvery { priceClient.getLatestPrice(InstrumentId("AAPL")) } returns PricePoint(
                    instrumentId = InstrumentId("AAPL"),
                    price = Money(BigDecimal("150.00"), Currency.getInstance("USD")),
                    timestamp = Instant.parse("2025-01-15T10:00:00Z"),
                    source = PriceSource.EXCHANGE,
                )

                testApplication {
                    application { module(priceClient) }
                    val response = client.get("/api/v1/prices/AAPL/latest")
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

        `when`("GET /api/v1/prices/{instrumentId}/latest with no data") {
            then("returns 404") {
                coEvery { priceClient.getLatestPrice(InstrumentId("UNKNOWN")) } returns null

                testApplication {
                    application { module(priceClient) }
                    val response = client.get("/api/v1/prices/UNKNOWN/latest")
                    response.status shouldBe HttpStatusCode.NotFound
                }
            }
        }

        `when`("GET /api/v1/prices/{instrumentId}/history without required params") {
            then("returns 400") {
                testApplication {
                    application { module(priceClient) }
                    val response = client.get("/api/v1/prices/AAPL/history")
                    response.status shouldBe HttpStatusCode.BadRequest
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body.containsKey("error") shouldBe true
                    body.containsKey("message") shouldBe true
                }
            }
        }
    }
})
