package com.kinetix.gateway.contract

import com.kinetix.gateway.client.*
import com.kinetix.gateway.module
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*

class GatewayErrorResponseContractTest : BehaviorSpec({

    given("gateway error responses") {

        `when`("invalid trade body sent to gateway") {
            then("returns 400 with { error, message } shape") {
                val positionClient = mockk<PositionServiceClient>()
                testApplication {
                    application { module(positionClient) }
                    val response = client.post("/api/v1/portfolios/port-1/trades") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"tradeId":"t-1","instrumentId":"AAPL","assetClass":"EQUITY","side":"BUY","quantity":"-1","priceAmount":"150","priceCurrency":"USD","tradedAt":"2025-01-15T10:00:00Z"}""")
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body.containsKey("error") shouldBe true
                    body.containsKey("message") shouldBe true
                }
            }
        }

        `when`("price service missing query params") {
            then("returns 400 with { error, message } shape") {
                val priceClient = mockk<PriceServiceClient>()
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
