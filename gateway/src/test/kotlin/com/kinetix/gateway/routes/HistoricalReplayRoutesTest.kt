package com.kinetix.gateway.routes

import com.kinetix.gateway.client.HistoricalReplayParams
import com.kinetix.gateway.client.HistoricalReplayResultSummary
import com.kinetix.gateway.client.PositionReplayImpactSummary
import com.kinetix.gateway.client.ReverseStressParams
import com.kinetix.gateway.client.ReverseStressResultSummary
import com.kinetix.gateway.client.InstrumentShockSummary
import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*

class HistoricalReplayRoutesTest : FunSpec({

    val riskClient = mockk<RiskServiceClient>()

    beforeEach {
        clearMocks(riskClient)
        coEvery { riskClient.calculateVaR(any()) } returns null
        coEvery { riskClient.getLatestVaR(any()) } returns null
    }

    test("POST /api/v1/risk/stress/{bookId}/historical-replay returns 200 with replay result") {
        coEvery { riskClient.runHistoricalReplay(any()) } returns HistoricalReplayResultSummary(
            scenarioName = "GFC_2008",
            totalPnlImpact = "-125000.00",
            positionImpacts = listOf(
                PositionReplayImpactSummary(
                    instrumentId = "AAPL",
                    assetClass = "EQUITY",
                    marketValue = "15500.00",
                    pnlImpact = "-125000.00",
                    dailyPnl = listOf("-50000.00", "-25000.00", "10000.00", "-40000.00", "-20000.00"),
                    proxyUsed = false,
                )
            ),
            windowStart = "2008-09-15",
            windowEnd = "2008-09-19",
            calculatedAt = "2025-01-15T10:00:00Z",
        )

        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/risk/stress/port-1/historical-replay") {
                contentType(ContentType.Application.Json)
                setBody("""{"instrumentReturns":[],"windowStart":"2008-09-15","windowEnd":"2008-09-19"}""")
            }
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["scenarioName"]?.jsonPrimitive?.content shouldBe "GFC_2008"
            body["totalPnlImpact"]?.jsonPrimitive?.content shouldBe "-125000.00"
            body["windowStart"]?.jsonPrimitive?.content shouldBe "2008-09-15"
            body["windowEnd"]?.jsonPrimitive?.content shouldBe "2008-09-19"
            val impacts = body["positionImpacts"]?.jsonArray
            impacts?.size shouldBe 1
            val impact = impacts!![0].jsonObject
            impact["instrumentId"]?.jsonPrimitive?.content shouldBe "AAPL"
            impact["proxyUsed"]?.jsonPrimitive?.content shouldBe "false"
        }
    }

    test("POST /api/v1/risk/stress/{bookId}/historical-replay passes bookId and instrument returns to client") {
        val captured = mutableListOf<HistoricalReplayParams>()
        coEvery { riskClient.runHistoricalReplay(capture(captured)) } returns HistoricalReplayResultSummary(
            scenarioName = "",
            totalPnlImpact = "0.00",
            positionImpacts = emptyList(),
            windowStart = null,
            windowEnd = null,
            calculatedAt = "2025-01-15T10:00:00Z",
        )

        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/risk/stress/port-1/historical-replay") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                        "instrumentReturns": [
                            {"instrumentId": "AAPL", "dailyReturns": [-0.05, -0.03, 0.01]}
                        ],
                        "windowStart": "2008-09-15",
                        "windowEnd": "2008-09-19"
                    }
                    """.trimIndent()
                )
            }
            response.status shouldBe HttpStatusCode.OK
            captured.size shouldBe 1
            captured[0].bookId shouldBe "port-1"
            captured[0].instrumentReturns.size shouldBe 1
            captured[0].instrumentReturns[0].instrumentId shouldBe "AAPL"
        }
    }

    test("POST /api/v1/risk/stress/{bookId}/reverse returns 200 with shock vector") {
        coEvery { riskClient.runReverseStress(any()) } returns ReverseStressResultSummary(
            shocks = listOf(InstrumentShockSummary(instrumentId = "AAPL", shock = "-0.100000")),
            achievedLoss = "100000.00",
            targetLoss = "100000.00",
            converged = true,
            calculatedAt = "2025-01-15T10:00:00Z",
        )

        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/risk/stress/port-1/reverse") {
                contentType(ContentType.Application.Json)
                setBody("""{"targetLoss":100000.0,"maxShock":-1.0}""")
            }
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["converged"]?.jsonPrimitive?.content shouldBe "true"
            body["targetLoss"]?.jsonPrimitive?.content shouldBe "100000.00"
            body["achievedLoss"]?.jsonPrimitive?.content shouldBe "100000.00"
            val shocks = body["shocks"]?.jsonArray
            shocks?.size shouldBe 1
            shocks!![0].jsonObject["instrumentId"]?.jsonPrimitive?.content shouldBe "AAPL"
        }
    }

    test("POST /api/v1/risk/stress/{bookId}/reverse returns 200 with converged=false when not converged") {
        coEvery { riskClient.runReverseStress(any()) } returns ReverseStressResultSummary(
            shocks = emptyList(),
            achievedLoss = "1500000.00",
            targetLoss = "5000000.00",
            converged = false,
            calculatedAt = "2025-01-15T10:00:00Z",
        )

        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/risk/stress/port-1/reverse") {
                contentType(ContentType.Application.Json)
                setBody("""{"targetLoss":5000000.0}""")
            }
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["converged"]?.jsonPrimitive?.content shouldBe "false"
        }
    }
})
