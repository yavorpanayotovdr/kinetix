package com.kinetix.acceptance

import com.kinetix.gateway.client.*
import com.kinetix.gateway.module
import com.kinetix.notification.delivery.InAppDeliveryService
import com.kinetix.notification.engine.RulesEngine
import com.kinetix.notification.module as notificationModule
import com.kinetix.notification.persistence.InMemoryAlertEventRepository
import com.kinetix.notification.persistence.InMemoryAlertRuleRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*

class ErrorResponseConsistencyIntegrationTest : BehaviorSpec({

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

        `when`("market-data missing query params") {
            then("returns 400 with { error, message } shape") {
                val marketDataClient = mockk<MarketDataServiceClient>()
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

    given("notification-service error responses") {

        `when`("POST /api/v1/notifications/rules with invalid type") {
            then("returns 400 with { error, message } shape") {
                testApplication {
                    application {
                        val rulesEngine = RulesEngine(InMemoryAlertRuleRepository())
                        val inAppDelivery = InAppDeliveryService(InMemoryAlertEventRepository())
                        notificationModule(rulesEngine, inAppDelivery)
                    }
                    val response = client.post("/api/v1/notifications/rules") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test","type":"INVALID_TYPE","threshold":1000.0,"operator":"GREATER_THAN","severity":"INFO","channels":["IN_APP"]}""")
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body.containsKey("error") shouldBe true
                    body.containsKey("message") shouldBe true
                }
            }
        }
    }
})
