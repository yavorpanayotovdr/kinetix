package com.kinetix.notification

import com.kinetix.notification.delivery.InAppDeliveryService
import com.kinetix.notification.engine.RulesEngine
import com.kinetix.notification.model.*
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

class AcknowledgeAlertAcceptanceTest : BehaviorSpec({

    fun setup(): Triple<InMemoryAlertEventRepository, InMemoryAlertAcknowledgementRepository, RulesEngine> {
        val eventRepo = InMemoryAlertEventRepository()
        val ackRepo = InMemoryAlertAcknowledgementRepository()
        val rulesEngine = RulesEngine(InMemoryAlertRuleRepository(), eventRepository = eventRepo)
        return Triple(eventRepo, ackRepo, rulesEngine)
    }

    given("a TRIGGERED alert exists") {
        val (eventRepo, ackRepo, rulesEngine) = setup()
        val alert = AlertEvent(
            id = "alert-ack-1",
            ruleId = "r1",
            ruleName = "VaR Breach",
            type = AlertType.VAR_BREACH,
            severity = Severity.CRITICAL,
            message = "VaR exceeded threshold",
            currentValue = 150_000.0,
            threshold = 100_000.0,
            bookId = "book-1",
            triggeredAt = Instant.parse("2025-01-15T10:00:00Z"),
        )
        eventRepo.save(alert)

        `when`("POST /alerts/{alertId}/acknowledge with valid body") {
            then("returns 200 with ACKNOWLEDGED status and persists acknowledgement") {
                testApplication {
                    application {
                        module(
                            rulesEngine,
                            InAppDeliveryService(eventRepo),
                            ackRepo,
                        )
                    }
                    val response = client.post("/api/v1/notifications/alerts/alert-ack-1/acknowledge") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"acknowledgedBy":"trader-1","notes":"Reviewing position"}""")
                    }
                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body["status"]?.jsonPrimitive?.content shouldBe "ACKNOWLEDGED"
                    body["id"]?.jsonPrimitive?.content shouldBe "alert-ack-1"
                }

                val ack = ackRepo.findByAlertId("alert-ack-1")
                ack?.acknowledgedBy shouldBe "trader-1"
                ack?.notes shouldBe "Reviewing position"
            }
        }

        `when`("POST /alerts/{alertId}/acknowledge for nonexistent alert") {
            then("returns 404") {
                val (eventRepo2, ackRepo2, rulesEngine2) = setup()
                testApplication {
                    application {
                        module(
                            rulesEngine2,
                            InAppDeliveryService(eventRepo2),
                            ackRepo2,
                        )
                    }
                    val response = client.post("/api/v1/notifications/alerts/nonexistent/acknowledge") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"acknowledgedBy":"trader-1"}""")
                    }
                    response.status shouldBe HttpStatusCode.NotFound
                }
            }
        }

        `when`("POST /alerts/{alertId}/acknowledge for already resolved alert") {
            then("returns 409 Conflict") {
                val (eventRepo3, ackRepo3, rulesEngine3) = setup()
                val resolvedAlert = AlertEvent(
                    id = "alert-resolved-1",
                    ruleId = "r1",
                    ruleName = "VaR Breach",
                    type = AlertType.VAR_BREACH,
                    severity = Severity.CRITICAL,
                    message = "VaR exceeded threshold",
                    currentValue = 150_000.0,
                    threshold = 100_000.0,
                    bookId = "book-1",
                    triggeredAt = Instant.parse("2025-01-15T10:00:00Z"),
                    status = AlertStatus.RESOLVED,
                    resolvedAt = Instant.now(),
                    resolvedReason = "AUTO_CLEARED",
                )
                eventRepo3.save(resolvedAlert)

                testApplication {
                    application {
                        module(
                            rulesEngine3,
                            InAppDeliveryService(eventRepo3),
                            ackRepo3,
                        )
                    }
                    val response = client.post("/api/v1/notifications/alerts/alert-resolved-1/acknowledge") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"acknowledgedBy":"trader-1"}""")
                    }
                    response.status shouldBe HttpStatusCode.Conflict
                }
            }
        }

        `when`("POST /alerts/{alertId}/acknowledge for already acknowledged alert") {
            then("returns 409 Conflict") {
                val (eventRepo4, ackRepo4, rulesEngine4) = setup()
                val ackedAlert = AlertEvent(
                    id = "alert-acked-1",
                    ruleId = "r1",
                    ruleName = "VaR Breach",
                    type = AlertType.VAR_BREACH,
                    severity = Severity.CRITICAL,
                    message = "VaR exceeded threshold",
                    currentValue = 150_000.0,
                    threshold = 100_000.0,
                    bookId = "book-1",
                    triggeredAt = Instant.parse("2025-01-15T10:00:00Z"),
                    status = AlertStatus.ACKNOWLEDGED,
                )
                eventRepo4.save(ackedAlert)

                testApplication {
                    application {
                        module(
                            rulesEngine4,
                            InAppDeliveryService(eventRepo4),
                            ackRepo4,
                        )
                    }
                    val response = client.post("/api/v1/notifications/alerts/alert-acked-1/acknowledge") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"acknowledgedBy":"trader-2"}""")
                    }
                    response.status shouldBe HttpStatusCode.Conflict
                }
            }
        }
    }
})
