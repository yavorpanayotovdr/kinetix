package com.kinetix.notification.contract

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

class NotificationErrorResponseContractTest : BehaviorSpec({

    given("notification-service error responses") {

        `when`("POST /api/v1/notifications/rules with invalid type") {
            then("returns 400 with { error, message } shape") {
                testApplication {
                    application {
                        val rulesEngine = RulesEngine(InMemoryAlertRuleRepository())
                        val inAppDelivery = InAppDeliveryService(InMemoryAlertEventRepository())
                        module(rulesEngine, inAppDelivery)
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
