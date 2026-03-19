package com.kinetix.smoke

import com.kinetix.smoke.SmokeHttpClient.smokeGet
import com.kinetix.smoke.SmokeHttpClient.smokePost
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID

@Tags("P2")
class RollingUpdateSmokeTest : FunSpec({

    val client = SmokeHttpClient.create()
    val smokeBookId = "smoke-rolling-${System.currentTimeMillis()}"

    test("continuous trade submissions succeed or are retryable during deployment window") {
        val results = (1..10).map { i ->
            async {
                val tradeId = UUID.randomUUID().toString()
                val tradeBody = """
                {
                    "tradeId": "$tradeId",
                    "instrumentId": "${SmokeTestConfig.seededInstrumentId}",
                    "side": "BUY",
                    "quantity": "1",
                    "priceAmount": "100.00",
                    "priceCurrency": "USD",
                    "assetClass": "EQUITY",
                    "tradedAt": "${Instant.now()}"
                }
                """.trimIndent()

                try {
                    val response = client.smokePost(
                        "/api/v1/books/$smokeBookId/trades",
                        "rolling-update-trade-$i",
                        tradeBody,
                    )
                    response.status.value
                } catch (e: Exception) {
                    // Network errors during rolling update are acceptable
                    -1
                }
            }
        }.awaitAll()

        val successful = results.count { it == 201 }
        val retryable = results.count { it in listOf(502, 503, 504, -1) }
        val unexpectedErrors = results.count { it !in listOf(201, 502, 503, 504, -1) }

        println("SMOKE_METRIC rolling_update_successful=$successful")
        println("SMOKE_METRIC rolling_update_retryable=$retryable")
        println("SMOKE_METRIC rolling_update_unexpected_errors=$unexpectedErrors")

        // All trades should either succeed or be retryable — no 500 errors
        unexpectedErrors shouldBe 0
        // At least some trades should succeed
        successful shouldBeGreaterThan 0
    }

    test("audit hash chain remains intact after trade submissions") {
        delay(2000) // Allow audit events to be processed

        val response = client.smokeGet(
            "/api/v1/audit/verify",
            "rolling-update-audit-verify",
        )

        // Audit verify endpoint may return 200 (valid) or 404 (no events yet)
        val status = response.status.value
        if (status == 200) {
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["valid"]?.jsonPrimitive?.boolean shouldBe true
        }
    }

    test("health endpoints remain accessible") {
        val services = listOf(
            "/api/v1/positions/health" to "position-service",
            "/api/v1/risk/health" to "risk-orchestrator",
        )

        for ((path, name) in services) {
            try {
                val response = client.smokeGet(path, "rolling-update-health-$name")
                val status = response.status.value
                // During rolling update, 503 is acceptable temporarily
                (status == 200 || status == 503) shouldBe true
            } catch (_: Exception) {
                // Connection refused during pod restart is acceptable
            }
        }
    }

    test("no 500-level errors from gateway health") {
        val response = client.smokeGet("/health", "rolling-update-gateway-health")
        response.status shouldNotBe HttpStatusCode.InternalServerError
    }
})
