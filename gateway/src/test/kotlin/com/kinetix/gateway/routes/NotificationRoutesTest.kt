package com.kinetix.gateway.routes

import com.kinetix.gateway.client.*
import com.kinetix.gateway.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*
import java.time.Instant

private val sampleRule = AlertRuleItem(
    id = "rule-1",
    name = "VaR Limit",
    type = "VAR_BREACH",
    threshold = 100_000.0,
    operator = "GREATER_THAN",
    severity = "CRITICAL",
    channels = listOf("IN_APP", "EMAIL"),
    enabled = true,
)

private val sampleAlert = AlertEventItem(
    id = "evt-1",
    ruleId = "rule-1",
    ruleName = "VaR Limit",
    type = "VAR_BREACH",
    severity = "CRITICAL",
    message = "VaR exceeded threshold",
    currentValue = 150_000.0,
    threshold = 100_000.0,
    portfolioId = "port-1",
    triggeredAt = Instant.parse("2025-01-15T10:00:00Z"),
)

class NotificationRoutesTest : FunSpec({

    val notificationClient = mockk<NotificationServiceClient>()

    beforeEach {
        clearMocks(notificationClient)
    }

    test("GET /api/v1/notifications/rules returns list") {
        coEvery { notificationClient.listRules() } returns listOf(sampleRule)

        testApplication {
            application { module(notificationClient) }
            val response = client.get("/api/v1/notifications/rules")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 1
            body[0].jsonObject["id"]?.jsonPrimitive?.content shouldBe "rule-1"
            body[0].jsonObject["name"]?.jsonPrimitive?.content shouldBe "VaR Limit"
            body[0].jsonObject["type"]?.jsonPrimitive?.content shouldBe "VAR_BREACH"
            body[0].jsonObject["threshold"]?.jsonPrimitive?.double shouldBe 100_000.0
        }
    }

    test("POST /api/v1/notifications/rules creates rule") {
        coEvery { notificationClient.createRule(any()) } returns sampleRule

        testApplication {
            application { module(notificationClient) }
            val response = client.post("/api/v1/notifications/rules") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"VaR Limit","type":"VAR_BREACH","threshold":100000.0,"operator":"GREATER_THAN","severity":"CRITICAL","channels":["IN_APP","EMAIL"]}""")
            }
            response.status shouldBe HttpStatusCode.Created
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["id"]?.jsonPrimitive?.content shouldBe "rule-1"
            body["name"]?.jsonPrimitive?.content shouldBe "VaR Limit"
        }
    }

    test("DELETE /api/v1/notifications/rules/{ruleId} removes rule") {
        coEvery { notificationClient.deleteRule("rule-1") } returns true

        testApplication {
            application { module(notificationClient) }
            val response = client.delete("/api/v1/notifications/rules/rule-1")
            response.status shouldBe HttpStatusCode.NoContent
        }
    }

    test("GET /api/v1/notifications/alerts returns recent") {
        coEvery { notificationClient.listAlerts(any()) } returns listOf(sampleAlert)

        testApplication {
            application { module(notificationClient) }
            val response = client.get("/api/v1/notifications/alerts")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 1
            body[0].jsonObject["id"]?.jsonPrimitive?.content shouldBe "evt-1"
            body[0].jsonObject["severity"]?.jsonPrimitive?.content shouldBe "CRITICAL"
            body[0].jsonObject["portfolioId"]?.jsonPrimitive?.content shouldBe "port-1"
        }
    }

    test("DELETE /api/v1/notifications/rules/{ruleId} returns 404 for nonexistent") {
        coEvery { notificationClient.deleteRule("nonexistent") } returns false

        testApplication {
            application { module(notificationClient) }
            val response = client.delete("/api/v1/notifications/rules/nonexistent")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
