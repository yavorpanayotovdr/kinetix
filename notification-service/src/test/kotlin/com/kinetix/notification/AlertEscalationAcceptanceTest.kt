package com.kinetix.notification

import com.kinetix.notification.delivery.InAppDeliveryService
import com.kinetix.notification.engine.RulesEngine
import com.kinetix.notification.model.AlertEvent
import com.kinetix.notification.model.AlertStatus
import com.kinetix.notification.model.AlertType
import com.kinetix.notification.model.Severity
import com.kinetix.notification.persistence.InMemoryAlertAcknowledgementRepository
import com.kinetix.notification.persistence.InMemoryAlertEventRepository
import com.kinetix.notification.persistence.InMemoryAlertRuleRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import java.time.Instant

class AlertEscalationAcceptanceTest : BehaviorSpec({

    fun setup(): Pair<InMemoryAlertEventRepository, RulesEngine> {
        val eventRepo = InMemoryAlertEventRepository()
        val rulesEngine = RulesEngine(InMemoryAlertRuleRepository(), eventRepository = eventRepo)
        return Pair(eventRepo, rulesEngine)
    }

    given("escalated alerts exist in the repository") {
        val (eventRepo, rulesEngine) = setup()

        val escalatedAlert = AlertEvent(
            id = "esc-1",
            ruleId = "r1",
            ruleName = "VaR Critical Limit",
            type = AlertType.VAR_BREACH,
            severity = Severity.CRITICAL,
            message = "VaR breach not acknowledged in time",
            currentValue = 250_000.0,
            threshold = 100_000.0,
            bookId = "book-1",
            triggeredAt = Instant.parse("2025-01-15T09:00:00Z"),
            status = AlertStatus.ESCALATED,
            acknowledgedAt = Instant.parse("2025-01-15T09:05:00Z"),
            escalatedAt = Instant.parse("2025-01-15T09:35:00Z"),
            escalatedTo = "risk-manager,cro",
        )
        val triggeredAlert = AlertEvent(
            id = "trig-1",
            ruleId = "r2",
            ruleName = "P&L Warning",
            type = AlertType.PNL_THRESHOLD,
            severity = Severity.WARNING,
            message = "P&L threshold exceeded",
            currentValue = 200_000.0,
            threshold = 150_000.0,
            bookId = "book-2",
            triggeredAt = Instant.parse("2025-01-15T10:00:00Z"),
            status = AlertStatus.TRIGGERED,
        )
        eventRepo.save(escalatedAlert)
        eventRepo.save(triggeredAlert)

        `when`("GET /api/v1/notifications/alerts/escalated") {
            then("returns only escalated alerts") {
                testApplication {
                    application {
                        module(rulesEngine, InAppDeliveryService(eventRepo), InMemoryAlertAcknowledgementRepository())
                    }
                    val response = client.get("/api/v1/notifications/alerts/escalated")
                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                    body.size shouldBe 1
                    body[0].jsonObject["id"]?.jsonPrimitive?.content shouldBe "esc-1"
                    body[0].jsonObject["status"]?.jsonPrimitive?.content shouldBe "ESCALATED"
                }
            }
        }

        `when`("GET /api/v1/notifications/alerts/escalated with no escalated alerts") {
            then("returns an empty list") {
                val (emptyRepo, freshRulesEngine) = setup()
                emptyRepo.save(triggeredAlert)

                testApplication {
                    application {
                        module(freshRulesEngine, InAppDeliveryService(emptyRepo), InMemoryAlertAcknowledgementRepository())
                    }
                    val response = client.get("/api/v1/notifications/alerts/escalated")
                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                    body.size shouldBe 0
                }
            }
        }
    }

    given("escalated alert response includes escalatedTo and escalatedAt fields") {
        val (eventRepo, rulesEngine) = setup()

        val escalatedAlert = AlertEvent(
            id = "esc-2",
            ruleId = "r1",
            ruleName = "VaR Critical Limit",
            type = AlertType.VAR_BREACH,
            severity = Severity.CRITICAL,
            message = "VaR breach",
            currentValue = 250_000.0,
            threshold = 100_000.0,
            bookId = "book-1",
            triggeredAt = Instant.parse("2025-01-15T09:00:00Z"),
            status = AlertStatus.ESCALATED,
            acknowledgedAt = Instant.parse("2025-01-15T09:05:00Z"),
            escalatedAt = Instant.parse("2025-01-15T09:35:00Z"),
            escalatedTo = "risk-manager,cro",
        )
        eventRepo.save(escalatedAlert)

        `when`("GET /api/v1/notifications/alerts returns all alerts") {
            then("escalated alert includes escalatedTo and escalatedAt in response") {
                testApplication {
                    application {
                        module(rulesEngine, InAppDeliveryService(eventRepo), InMemoryAlertAcknowledgementRepository())
                    }
                    val response = client.get("/api/v1/notifications/alerts")
                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                    val alert = body.first { it.jsonObject["id"]?.jsonPrimitive?.content == "esc-2" }.jsonObject
                    alert["escalatedTo"]?.jsonPrimitive?.content shouldBe "risk-manager,cro"
                    alert["escalatedAt"]?.jsonPrimitive?.content shouldBe "2025-01-15T09:35:00Z"
                }
            }
        }
    }

    given("attempting to acknowledge an already ESCALATED alert") {
        val (eventRepo, rulesEngine) = setup()

        val escalatedAlert = AlertEvent(
            id = "esc-3",
            ruleId = "r1",
            ruleName = "VaR Critical Limit",
            type = AlertType.VAR_BREACH,
            severity = Severity.CRITICAL,
            message = "VaR breach",
            currentValue = 250_000.0,
            threshold = 100_000.0,
            bookId = "book-1",
            triggeredAt = Instant.parse("2025-01-15T09:00:00Z"),
            status = AlertStatus.ESCALATED,
            acknowledgedAt = Instant.parse("2025-01-15T09:05:00Z"),
            escalatedAt = Instant.parse("2025-01-15T09:35:00Z"),
            escalatedTo = "risk-manager,cro",
        )
        eventRepo.save(escalatedAlert)

        `when`("POST /alerts/{alertId}/acknowledge on ESCALATED alert") {
            then("returns 409 Conflict — ESCALATED alerts must be resolved, not re-acknowledged") {
                testApplication {
                    application {
                        module(rulesEngine, InAppDeliveryService(eventRepo), InMemoryAlertAcknowledgementRepository())
                    }
                    val response = client.post("/api/v1/notifications/alerts/esc-3/acknowledge") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"acknowledgedBy":"trader-1"}""")
                    }
                    response.status shouldBe HttpStatusCode.Conflict
                }
            }
        }
    }

    given("GET /api/v1/notifications/alerts with status=ESCALATED filter") {
        val (eventRepo, rulesEngine) = setup()

        val escalatedAlert = AlertEvent(
            id = "esc-4",
            ruleId = "r1",
            ruleName = "VaR Breach",
            type = AlertType.VAR_BREACH,
            severity = Severity.CRITICAL,
            message = "breach",
            currentValue = 200_000.0,
            threshold = 100_000.0,
            bookId = "book-1",
            triggeredAt = Instant.parse("2025-01-15T09:00:00Z"),
            status = AlertStatus.ESCALATED,
            escalatedAt = Instant.parse("2025-01-15T09:35:00Z"),
            escalatedTo = "risk-manager,cro",
        )
        val triggeredAlert = AlertEvent(
            id = "trig-2",
            ruleId = "r2",
            ruleName = "P&L Warning",
            type = AlertType.PNL_THRESHOLD,
            severity = Severity.WARNING,
            message = "P&L exceeded",
            currentValue = 200_000.0,
            threshold = 150_000.0,
            bookId = "book-2",
            triggeredAt = Instant.parse("2025-01-15T10:00:00Z"),
            status = AlertStatus.TRIGGERED,
        )
        eventRepo.save(escalatedAlert)
        eventRepo.save(triggeredAlert)

        `when`("GET /api/v1/notifications/alerts?status=ESCALATED") {
            then("returns only escalated alerts") {
                testApplication {
                    application {
                        module(rulesEngine, InAppDeliveryService(eventRepo), InMemoryAlertAcknowledgementRepository())
                    }
                    val response = client.get("/api/v1/notifications/alerts?status=ESCALATED")
                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                    body.size shouldBe 1
                    body[0].jsonObject["id"]?.jsonPrimitive?.content shouldBe "esc-4"
                }
            }
        }
    }
})
