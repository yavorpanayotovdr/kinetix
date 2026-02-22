package com.kinetix.acceptance

import com.kinetix.notification.delivery.InAppDeliveryService
import com.kinetix.notification.engine.RulesEngine
import com.kinetix.notification.module
import com.kinetix.notification.persistence.InMemoryAlertEventRepository
import com.kinetix.notification.persistence.InMemoryAlertRuleRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*

class GatewayNotificationContractIntegrationTest : BehaviorSpec({

    given("the notification-service running") {

        `when`("POST /api/v1/notifications/rules with valid body") {
            then("returns 201 with rule shape matching gateway expectations") {
                testApplication {
                    application {
                        module(
                            RulesEngine(InMemoryAlertRuleRepository()),
                            InAppDeliveryService(InMemoryAlertEventRepository()),
                        )
                    }
                    val response = client.post("/api/v1/notifications/rules") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"VaR Limit","type":"VAR_BREACH","threshold":100000.0,"operator":"GREATER_THAN","severity":"CRITICAL","channels":["IN_APP","EMAIL"]}""")
                    }
                    response.status shouldBe HttpStatusCode.Created
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    // Verify shape matches what gateway's HttpNotificationServiceClient expects
                    body.containsKey("id") shouldBe true
                    body["name"]?.jsonPrimitive?.content shouldBe "VaR Limit"
                    body["type"]?.jsonPrimitive?.content shouldBe "VAR_BREACH"
                    body["threshold"]?.jsonPrimitive?.double shouldBe 100000.0
                    body["operator"]?.jsonPrimitive?.content shouldBe "GREATER_THAN"
                    body["severity"]?.jsonPrimitive?.content shouldBe "CRITICAL"
                    body["channels"]?.jsonArray?.map { it.jsonPrimitive.content } shouldBe listOf("IN_APP", "EMAIL")
                    body["enabled"]?.jsonPrimitive?.boolean shouldBe true
                }
            }
        }

        `when`("GET /api/v1/notifications/rules after creating a rule") {
            then("returns 200 with array shape") {
                testApplication {
                    application {
                        module(
                            RulesEngine(InMemoryAlertRuleRepository()),
                            InAppDeliveryService(InMemoryAlertEventRepository()),
                        )
                    }
                    // Create a rule first
                    client.post("/api/v1/notifications/rules") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"name":"Test","type":"VAR_BREACH","threshold":1000.0,"operator":"GREATER_THAN","severity":"INFO","channels":["IN_APP"]}""")
                    }
                    val response = client.get("/api/v1/notifications/rules")
                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                    body.size shouldBe 1
                    body[0].jsonObject.containsKey("id") shouldBe true
                    body[0].jsonObject.containsKey("name") shouldBe true
                    body[0].jsonObject.containsKey("type") shouldBe true
                }
            }
        }

        `when`("DELETE /api/v1/notifications/rules/{ruleId} for nonexistent rule") {
            then("returns 404") {
                testApplication {
                    application {
                        module(
                            RulesEngine(InMemoryAlertRuleRepository()),
                            InAppDeliveryService(InMemoryAlertEventRepository()),
                        )
                    }
                    val response = client.delete("/api/v1/notifications/rules/nonexistent")
                    response.status shouldBe HttpStatusCode.NotFound
                }
            }
        }

        `when`("GET /api/v1/notifications/alerts with no alerts") {
            then("returns 200 with empty array") {
                testApplication {
                    application {
                        module(
                            RulesEngine(InMemoryAlertRuleRepository()),
                            InAppDeliveryService(InMemoryAlertEventRepository()),
                        )
                    }
                    val response = client.get("/api/v1/notifications/alerts")
                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                    body.size shouldBe 0
                }
            }
        }
    }
})
