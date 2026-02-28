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

private val sampleSodStatus = SodBaselineStatusSummary(
    exists = true,
    baselineDate = "2025-01-15",
    snapshotType = "MANUAL",
    createdAt = "2025-01-15T08:00:00Z",
    sourceJobId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
    calculationType = "PARAMETRIC",
)

private val samplePnlAttribution = PnlAttributionSummary(
    portfolioId = "port-1",
    date = "2025-01-15",
    totalPnl = "15000.00",
    deltaPnl = "8000.00",
    gammaPnl = "2500.00",
    vegaPnl = "3000.00",
    thetaPnl = "-1500.00",
    rhoPnl = "500.00",
    unexplainedPnl = "2500.00",
    positionAttributions = listOf(
        PositionPnlAttributionSummary(
            instrumentId = "AAPL",
            assetClass = "EQUITY",
            totalPnl = "8000.00",
            deltaPnl = "5000.00",
            gammaPnl = "1200.00",
            vegaPnl = "1500.00",
            thetaPnl = "-800.00",
            rhoPnl = "300.00",
            unexplainedPnl = "800.00",
        ),
    ),
    calculatedAt = "2025-01-15T10:30:00Z",
)

class SodSnapshotRoutesTest : FunSpec({

    val riskClient = mockk<RiskServiceClient>()

    beforeEach {
        clearMocks(riskClient)
        coEvery { riskClient.calculateVaR(any()) } returns null
        coEvery { riskClient.getLatestVaR(any()) } returns null
    }

    test("GET /api/v1/risk/sod-snapshot/{portfolioId}/status returns 200 with baseline status") {
        coEvery { riskClient.getSodBaselineStatus("port-1") } returns sampleSodStatus

        testApplication {
            application { module(riskClient) }
            val response = client.get("/api/v1/risk/sod-snapshot/port-1/status")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["exists"]?.jsonPrimitive?.boolean shouldBe true
            body["baselineDate"]?.jsonPrimitive?.content shouldBe "2025-01-15"
            body["snapshotType"]?.jsonPrimitive?.content shouldBe "MANUAL"
            body["createdAt"]?.jsonPrimitive?.content shouldBe "2025-01-15T08:00:00Z"
            body["sourceJobId"]?.jsonPrimitive?.content shouldBe "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
            body["calculationType"]?.jsonPrimitive?.content shouldBe "PARAMETRIC"
        }
    }

    test("GET /api/v1/risk/sod-snapshot/{portfolioId}/status returns 404 when no baseline exists") {
        coEvery { riskClient.getSodBaselineStatus("port-1") } returns null

        testApplication {
            application { module(riskClient) }
            val response = client.get("/api/v1/risk/sod-snapshot/port-1/status")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /api/v1/risk/sod-snapshot/{portfolioId} returns 201 with created baseline") {
        coEvery { riskClient.createSodSnapshot("port-1", null) } returns sampleSodStatus

        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/risk/sod-snapshot/port-1")
            response.status shouldBe HttpStatusCode.Created
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["exists"]?.jsonPrimitive?.boolean shouldBe true
            body["snapshotType"]?.jsonPrimitive?.content shouldBe "MANUAL"
        }
    }

    test("POST /api/v1/risk/sod-snapshot/{portfolioId} forwards jobId query parameter") {
        val jobId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        coEvery { riskClient.createSodSnapshot("port-1", jobId) } returns sampleSodStatus

        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/risk/sod-snapshot/port-1?jobId=$jobId")
            response.status shouldBe HttpStatusCode.Created
            coVerify { riskClient.createSodSnapshot("port-1", jobId) }
        }
    }

    test("DELETE /api/v1/risk/sod-snapshot/{portfolioId} returns 204") {
        coEvery { riskClient.resetSodBaseline("port-1") } just Runs

        testApplication {
            application { module(riskClient) }
            val response = client.delete("/api/v1/risk/sod-snapshot/port-1")
            response.status shouldBe HttpStatusCode.NoContent
            coVerify { riskClient.resetSodBaseline("port-1") }
        }
    }

    test("GET /api/v1/risk/pnl-attribution/{portfolioId} returns 200 with attribution data") {
        coEvery { riskClient.getPnlAttribution("port-1", null) } returns samplePnlAttribution

        testApplication {
            application { module(riskClient) }
            val response = client.get("/api/v1/risk/pnl-attribution/port-1")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["portfolioId"]?.jsonPrimitive?.content shouldBe "port-1"
            body["totalPnl"]?.jsonPrimitive?.content shouldBe "15000.00"
            val positions = body["positionAttributions"]?.jsonArray
            positions?.size shouldBe 1
            positions!![0].jsonObject["instrumentId"]?.jsonPrimitive?.content shouldBe "AAPL"
        }
    }

    test("GET /api/v1/risk/pnl-attribution/{portfolioId} returns 404 when no data") {
        coEvery { riskClient.getPnlAttribution("port-1", null) } returns null

        testApplication {
            application { module(riskClient) }
            val response = client.get("/api/v1/risk/pnl-attribution/port-1")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /api/v1/risk/pnl-attribution/{portfolioId}/compute returns 200 with computed data") {
        coEvery { riskClient.computePnlAttribution("port-1") } returns samplePnlAttribution

        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/risk/pnl-attribution/port-1/compute")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["totalPnl"]?.jsonPrimitive?.content shouldBe "15000.00"
            body["deltaPnl"]?.jsonPrimitive?.content shouldBe "8000.00"
        }
    }
})
