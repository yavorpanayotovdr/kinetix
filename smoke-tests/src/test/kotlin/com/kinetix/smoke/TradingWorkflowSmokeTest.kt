package com.kinetix.smoke

import com.kinetix.smoke.SmokeHttpClient.smokeGet
import com.kinetix.smoke.SmokeHttpClient.smokePost
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Tags("P1")
class TradingWorkflowSmokeTest : FunSpec({

    val client = SmokeHttpClient.create()
    val smokeBookId = "smoke-${System.currentTimeMillis()}"
    var bookedTradeId: String? = null

    test("trade booking round-trip — POST trade, verify position appears") {
        val tradeId = UUID.randomUUID().toString()
        val tradeBody = """
        {
            "tradeId": "$tradeId",
            "instrumentId": "${SmokeTestConfig.seededInstrumentId}",
            "side": "BUY",
            "quantity": "10",
            "priceAmount": "150.00",
            "priceCurrency": "USD",
            "assetClass": "EQUITY",
            "tradedAt": "${Instant.now()}"
        }
        """.trimIndent()

        val start = System.currentTimeMillis()
        val response = client.smokePost(
            "/api/v1/books/$smokeBookId/trades",
            "trade-booking",
            tradeBody,
        )
        val elapsed = System.currentTimeMillis() - start
        println("SMOKE_METRIC trade_booking_ms=$elapsed")

        response.status shouldBe HttpStatusCode.Created
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val trade = body["trade"]?.jsonObject
        trade.shouldNotBeNull()
        bookedTradeId = trade["tradeId"]?.jsonPrimitive?.content
        bookedTradeId.shouldNotBeNull()

        // Verify position appears
        val posResponse = client.smokeGet(
            "/api/v1/books/$smokeBookId/positions",
            "position-check",
        )
        posResponse.status shouldBe HttpStatusCode.OK
        val positions = Json.parseToJsonElement(posResponse.bodyAsText()).jsonArray
        positions.size shouldBeGreaterThan 0

        val position = positions.first().jsonObject
        val qty = position["quantity"]?.jsonPrimitive?.content?.let { BigDecimal(it) }
        qty.shouldNotBeNull()
        qty shouldBeGreaterThan BigDecimal.ZERO
    }

    test("audit event created — trade booking produces audit trail") {
        bookedTradeId.shouldNotBeNull()

        val auditEvent = PollUtils.pollUntil(
            timeoutMs = 30_000,
            intervalMs = 1_000,
            description = "audit event for trade $bookedTradeId",
        ) {
            val response = client.smokeGet(
                "/api/v1/audit/events?portfolioId=$smokeBookId",
                "audit-check",
            )
            if (response.status != HttpStatusCode.OK) return@pollUntil null
            val events = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            if (events.isEmpty()) return@pollUntil null
            events.firstOrNull { event ->
                event.jsonObject["tradeId"]?.jsonPrimitive?.content == bookedTradeId
            }
        }

        val eventObj = auditEvent.jsonObject
        eventObj["tradeId"]?.jsonPrimitive?.content shouldBe bookedTradeId
    }

    test("audit hash chain intact — verify endpoint returns valid") {
        val start = System.currentTimeMillis()
        val response = client.smokeGet(
            "/api/v1/audit/verify",
            "audit-verify",
        )
        val elapsed = System.currentTimeMillis() - start
        println("SMOKE_METRIC audit_verify_ms=$elapsed")

        response.status shouldBe HttpStatusCode.OK
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        body["valid"]?.jsonPrimitive?.boolean shouldBe true
    }
})
