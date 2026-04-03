package com.kinetix.audit

import com.kinetix.audit.dlq.DlqMessage
import com.kinetix.audit.dlq.DlqReplayService
import com.kinetix.audit.model.AuditEvent
import com.kinetix.audit.persistence.AuditEventRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*
import java.time.Instant

class DlqReplayAcceptanceTest : FunSpec({

    val repository = mockk<AuditEventRepository>()

    beforeEach { clearMocks(repository) }

    test("POST /api/v1/audit/dlq/replay returns 200 with replay summary for successful replay") {
        coEvery { repository.save(any()) } just runs

        val dlqMessages = listOf(
            DlqMessage(key = "k1", value = tradeEventJson("t-1")),
            DlqMessage(key = "k2", value = tradeEventJson("t-2")),
        )
        val replayService = DlqReplayService(repository, messageSource = { dlqMessages })

        testApplication {
            application { module(repository, dlqReplayService = replayService) }
            val response = client.post("/api/v1/audit/dlq/replay")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["successCount"]?.jsonPrimitive?.int shouldBe 2
            body["failureCount"]?.jsonPrimitive?.int shouldBe 0
            body["total"]?.jsonPrimitive?.int shouldBe 2
        }
    }

    test("POST /api/v1/audit/dlq/replay returns 200 with failure count when some events fail") {
        coEvery { repository.save(any()) } just runs

        val dlqMessages = listOf(
            DlqMessage(key = "k1", value = """{"invalid":"json"}"""),
            DlqMessage(key = "k2", value = tradeEventJson("t-2")),
        )
        val replayService = DlqReplayService(repository, messageSource = { dlqMessages })

        testApplication {
            application { module(repository, dlqReplayService = replayService) }
            val response = client.post("/api/v1/audit/dlq/replay")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["successCount"]?.jsonPrimitive?.int shouldBe 1
            body["failureCount"]?.jsonPrimitive?.int shouldBe 1
            body["total"]?.jsonPrimitive?.int shouldBe 2
        }
    }

    test("POST /api/v1/audit/dlq/replay returns 200 with zero counts when DLQ is empty") {
        val replayService = DlqReplayService(repository, messageSource = { emptyList() })

        testApplication {
            application { module(repository, dlqReplayService = replayService) }
            val response = client.post("/api/v1/audit/dlq/replay")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["successCount"]?.jsonPrimitive?.int shouldBe 0
            body["failureCount"]?.jsonPrimitive?.int shouldBe 0
            body["total"]?.jsonPrimitive?.int shouldBe 0
        }
    }
})

private fun tradeEventJson(tradeId: String) = """
{
  "tradeId": "$tradeId",
  "bookId": "book-1",
  "instrumentId": "AAPL",
  "assetClass": "EQUITY",
  "side": "BUY",
  "quantity": "100",
  "priceAmount": "150.00",
  "priceCurrency": "USD",
  "tradedAt": "2026-01-15T10:00:00Z",
  "eventType": "NEW",
  "status": "LIVE",
  "auditEventType": "TRADE_BOOKED"
}
""".trimIndent()
