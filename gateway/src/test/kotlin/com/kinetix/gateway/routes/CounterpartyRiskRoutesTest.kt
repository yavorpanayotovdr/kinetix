package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private val sampleExposure = buildJsonObject {
    put("counterpartyId", "CP-GS")
    put("calculatedAt", "2026-03-24T10:00:00Z")
    put("currentNetExposure", 2_000_000.0)
    put("peakPfe", 1_800_000.0)
    put("cva", 12_500.0)
    put("cvaEstimated", false)
    put("currency", "USD")
    putJsonArray("pfeProfile") {}
}

private val sampleCva = buildJsonObject {
    put("counterpartyId", "CP-GS")
    put("cva", 12_500.0)
    put("isEstimated", false)
    put("hazardRate", 0.0065)
    put("pd1y", 0.0065)
}

class CounterpartyRiskRoutesTest : FunSpec({

    val riskClient = mockk<RiskServiceClient>(relaxed = true)

    beforeEach {
        clearMocks(riskClient)
        coEvery { riskClient.calculateVaR(any()) } returns null
        coEvery { riskClient.getLatestVaR(any()) } returns null
    }

    test("GET /api/v1/counterparty-risk returns all counterparty exposures") {
        val exposureList = buildJsonArray { add(sampleExposure) }
        coEvery { riskClient.getAllCounterpartyExposures() } returns exposureList

        testApplication {
            application { module(riskClient) }
            val response = client.get("/api/v1/counterparty-risk")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe exposureList.toString()
        }
    }

    test("GET /api/v1/counterparty-risk/{id} returns exposure for known counterparty") {
        coEvery { riskClient.getCounterpartyExposure("CP-GS") } returns sampleExposure

        testApplication {
            application { module(riskClient) }
            val response = client.get("/api/v1/counterparty-risk/CP-GS")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe sampleExposure.toString()
        }
    }

    test("GET /api/v1/counterparty-risk/{id} returns 404 for unknown counterparty") {
        coEvery { riskClient.getCounterpartyExposure("CP-UNKNOWN") } returns null

        testApplication {
            application { module(riskClient) }
            val response = client.get("/api/v1/counterparty-risk/CP-UNKNOWN")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/counterparty-risk/{id}/history returns history") {
        val historyList = buildJsonArray { add(sampleExposure) }
        coEvery { riskClient.getCounterpartyExposureHistory("CP-GS", 90) } returns historyList

        testApplication {
            application { module(riskClient) }
            val response = client.get("/api/v1/counterparty-risk/CP-GS/history")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe historyList.toString()
        }
    }

    test("POST /api/v1/counterparty-risk/{id}/pfe triggers PFE computation") {
        coEvery { riskClient.computeCounterpartyPFE("CP-GS", any()) } returns sampleExposure

        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/counterparty-risk/CP-GS/pfe") {
                contentType(ContentType.Application.Json)
                setBody("""{"positions":[]}""")
            }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe sampleExposure.toString()
        }
    }

    test("POST /api/v1/counterparty-risk/{id}/cva returns CVA for known counterparty") {
        coEvery { riskClient.computeCounterpartyCVA("CP-GS") } returns sampleCva

        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/counterparty-risk/CP-GS/cva")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe sampleCva.toString()
        }
    }

    test("POST /api/v1/counterparty-risk/{id}/cva returns 404 when no PFE snapshot") {
        coEvery { riskClient.computeCounterpartyCVA("CP-NEW") } returns null

        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/counterparty-risk/CP-NEW/cva")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
