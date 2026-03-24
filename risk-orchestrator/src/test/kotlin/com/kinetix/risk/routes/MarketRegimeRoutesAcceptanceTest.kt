package com.kinetix.risk.routes

import com.kinetix.risk.model.AdaptiveVaRParameters
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.MarketRegime
import com.kinetix.risk.model.MarketRegimeHistory
import com.kinetix.risk.model.RegimeSignals
import com.kinetix.risk.model.RegimeState
import com.kinetix.risk.persistence.MarketRegimeRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.util.UUID

private fun crisisState() = RegimeState(
    regime = MarketRegime.CRISIS,
    detectedAt = Instant.parse("2026-03-24T14:30:00Z"),
    confidence = 0.87,
    signals = RegimeSignals(
        realisedVol20d = 0.28,
        crossAssetCorrelation = 0.80,
        creditSpreadBps = 220.0,
        pnlVolatility = 0.07,
    ),
    varParameters = AdaptiveVaRParameters(
        calculationType = CalculationType.MONTE_CARLO,
        confidenceLevel = ConfidenceLevel.CL_99,
        timeHorizonDays = 5,
        correlationMethod = "stressed",
        numSimulations = 50_000,
    ),
    consecutiveObservations = 3,
    isConfirmed = true,
    degradedInputs = false,
)

private fun historyItem(id: UUID = UUID.randomUUID(), regime: String = "CRISIS") = MarketRegimeHistory(
    id = id,
    regime = MarketRegime.valueOf(regime),
    startedAt = Instant.parse("2026-03-24T14:30:00Z"),
    endedAt = null,
    durationMs = null,
    signals = RegimeSignals(realisedVol20d = 0.28, crossAssetCorrelation = 0.80),
    varParameters = AdaptiveVaRParameters(
        calculationType = CalculationType.MONTE_CARLO,
        confidenceLevel = ConfidenceLevel.CL_99,
        timeHorizonDays = 5,
        correlationMethod = "stressed",
        numSimulations = 50_000,
    ),
    confidence = 0.87,
    degradedInputs = false,
    consecutiveObservations = 3,
)

class MarketRegimeRoutesAcceptanceTest : FunSpec({

    val repository = mockk<MarketRegimeRepository>()

    fun testApp(currentState: RegimeState, block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            routing {
                marketRegimeRoutes(
                    currentStateProvider = { currentState },
                    repository = repository,
                )
            }
            block()
        }
    }

    test("GET /api/v1/risk/regime/current returns 200 with regime details") {
        testApp(currentState = crisisState()) {
            val response = client.get("/api/v1/risk/regime/current")

            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("GET /api/v1/risk/regime/current response body contains regime CRISIS") {
        testApp(currentState = crisisState()) {
            val response = client.get("/api/v1/risk/regime/current")
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            body["regime"]!!.jsonPrimitive.content shouldBe "CRISIS"
        }
    }

    test("GET /api/v1/risk/regime/current response body contains isConfirmed true") {
        testApp(currentState = crisisState()) {
            val response = client.get("/api/v1/risk/regime/current")
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            body["isConfirmed"]!!.jsonPrimitive.content shouldBe "true"
        }
    }

    test("GET /api/v1/risk/regime/current response body contains effective VaR parameters") {
        testApp(currentState = crisisState()) {
            val response = client.get("/api/v1/risk/regime/current")
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val params = body["varParameters"]!!.jsonObject

            params["calculationType"]!!.jsonPrimitive.content shouldBe "MONTE_CARLO"
            params["confidenceLevel"]!!.jsonPrimitive.content shouldBe "CL_99"
        }
    }

    test("GET /api/v1/risk/regime/history returns 200 with items array") {
        coEvery { repository.findRecent(any()) } returns listOf(historyItem(), historyItem())

        testApp(currentState = crisisState()) {
            val response = client.get("/api/v1/risk/regime/history")

            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["items"]!!.jsonArray.size shouldBe 2
        }
    }

    test("GET /api/v1/risk/regime/history respects limit query parameter") {
        coEvery { repository.findRecent(5) } returns listOf(historyItem())

        testApp(currentState = crisisState()) {
            val response = client.get("/api/v1/risk/regime/history?limit=5")

            response.status shouldBe HttpStatusCode.OK
        }
    }

    test("GET /api/v1/risk/regime/history total matches items size") {
        coEvery { repository.findRecent(any()) } returns listOf(historyItem(), historyItem(), historyItem())

        testApp(currentState = crisisState()) {
            val response = client.get("/api/v1/risk/regime/history")
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

            body["total"]!!.jsonPrimitive.content.toInt() shouldBe 3
        }
    }
})
