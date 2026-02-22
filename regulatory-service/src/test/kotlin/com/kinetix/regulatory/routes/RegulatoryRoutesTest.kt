package com.kinetix.regulatory.routes

import com.kinetix.regulatory.client.RiskOrchestratorClient
import com.kinetix.regulatory.dto.FrtbResultResponse
import com.kinetix.regulatory.dto.RiskClassChargeDto
import com.kinetix.regulatory.model.FrtbCalculationRecord
import com.kinetix.regulatory.model.RiskClassCharge
import com.kinetix.regulatory.module
import com.kinetix.regulatory.persistence.FrtbCalculationRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant

class RegulatoryRoutesTest : FunSpec({

    val sampleFrtbResult = FrtbResultResponse(
        portfolioId = "port-1",
        sbmCharges = listOf(
            RiskClassChargeDto("GIRR", "100.00", "50.00", "25.00", "175.00"),
        ),
        totalSbmCharge = "175.00",
        grossJtd = "200.00",
        hedgeBenefit = "50.00",
        netDrc = "150.00",
        exoticNotional = "10.00",
        otherNotional = "5.00",
        totalRrao = "15.00",
        totalCapitalCharge = "340.00",
        calculatedAt = "2025-01-15T10:00:00Z",
    )

    val sampleRecord = FrtbCalculationRecord(
        id = "calc-1",
        portfolioId = "port-1",
        totalSbmCharge = 175.0,
        grossJtd = 200.0,
        hedgeBenefit = 50.0,
        netDrc = 150.0,
        exoticNotional = 10.0,
        otherNotional = 5.0,
        totalRrao = 15.0,
        totalCapitalCharge = 340.0,
        sbmCharges = listOf(RiskClassCharge("GIRR", 100.0, 50.0, 25.0, 175.0)),
        calculatedAt = Instant.parse("2025-01-15T10:00:00Z"),
        storedAt = Instant.parse("2025-01-15T10:00:01Z"),
    )

    test("POST /calculate calls risk-orchestrator and stores result") {
        val repository = mockk<FrtbCalculationRepository>()
        val client = mockk<RiskOrchestratorClient>()
        coEvery { client.calculateFrtb("port-1") } returns sampleFrtbResult
        coEvery { repository.save(any()) } returns Unit

        testApplication {
            application { module(repository, client) }
            val response = this.client.post("/api/v1/regulatory/frtb/port-1/calculate")
            response.status shouldBe HttpStatusCode.Created
            val body = response.bodyAsText()
            body shouldContain "\"portfolioId\":\"port-1\""
            body shouldContain "\"totalCapitalCharge\":\"340.00\""
        }
    }

    test("GET /history returns paginated results") {
        val repository = mockk<FrtbCalculationRepository>()
        val client = mockk<RiskOrchestratorClient>()
        coEvery { repository.findByPortfolioId("port-1", 20, 0) } returns listOf(sampleRecord)

        testApplication {
            application { module(repository, client) }
            val response = this.client.get("/api/v1/regulatory/frtb/port-1/history")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "\"calculations\""
            body shouldContain "\"portfolioId\":\"port-1\""
        }
    }

    test("GET /latest returns most recent calculation") {
        val repository = mockk<FrtbCalculationRepository>()
        val client = mockk<RiskOrchestratorClient>()
        coEvery { repository.findLatestByPortfolioId("port-1") } returns sampleRecord

        testApplication {
            application { module(repository, client) }
            val response = this.client.get("/api/v1/regulatory/frtb/port-1/latest")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "\"totalCapitalCharge\":\"340.00\""
        }
    }

    test("GET /latest returns 404 when no calculations exist") {
        val repository = mockk<FrtbCalculationRepository>()
        val client = mockk<RiskOrchestratorClient>()
        coEvery { repository.findLatestByPortfolioId("port-2") } returns null

        testApplication {
            application { module(repository, client) }
            val response = this.client.get("/api/v1/regulatory/frtb/port-2/latest")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
