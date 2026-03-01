package com.kinetix.regulatory.routes

import com.kinetix.regulatory.client.RiskOrchestratorClient
import com.kinetix.regulatory.model.BacktestResultRecord
import com.kinetix.regulatory.module
import com.kinetix.regulatory.persistence.BacktestResultRepository
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

class BacktestRoutesTest : FunSpec({

    val sampleRecord = BacktestResultRecord(
        id = "bt-1",
        portfolioId = "port-1",
        calculationType = "PARAMETRIC",
        confidenceLevel = 0.99,
        totalDays = 250,
        violationCount = 3,
        violationRate = 0.012,
        kupiecStatistic = 0.15,
        kupiecPValue = 0.70,
        kupiecPass = true,
        christoffersenStatistic = 0.08,
        christoffersenPValue = 0.78,
        christoffersenPass = true,
        trafficLightZone = "GREEN",
        calculatedAt = Instant.parse("2026-01-15T10:00:00Z"),
    )

    test("POST /backtest triggers backtest and returns 201") {
        val frtbRepo = mockk<FrtbCalculationRepository>()
        val backtestRepo = mockk<BacktestResultRepository>()
        val client = mockk<RiskOrchestratorClient>()
        coEvery { backtestRepo.save(any()) } returns Unit

        testApplication {
            application { module(frtbRepo, client, backtestRepo) }
            val response = this.client.post("/api/v1/regulatory/backtest/port-1") {
                contentType(ContentType.Application.Json)
                setBody("""{"dailyVarPredictions":[100.0,100.0,100.0],"dailyPnl":[-50.0,-150.0,-80.0],"confidenceLevel":0.99}""")
            }
            response.status shouldBe HttpStatusCode.Created
            val body = response.bodyAsText()
            body shouldContain "\"portfolioId\":\"port-1\""
            body shouldContain "\"violationCount\":1"
            body shouldContain "\"trafficLightZone\":\"GREEN\""
        }
    }

    test("GET /backtest/latest returns most recent result") {
        val frtbRepo = mockk<FrtbCalculationRepository>()
        val backtestRepo = mockk<BacktestResultRepository>()
        val client = mockk<RiskOrchestratorClient>()
        coEvery { backtestRepo.findLatestByPortfolioId("port-1") } returns sampleRecord

        testApplication {
            application { module(frtbRepo, client, backtestRepo) }
            val response = this.client.get("/api/v1/regulatory/backtest/port-1/latest")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "\"trafficLightZone\":\"GREEN\""
            body shouldContain "\"kupiecPass\":true"
        }
    }

    test("GET /backtest/latest returns 404 when no results exist") {
        val frtbRepo = mockk<FrtbCalculationRepository>()
        val backtestRepo = mockk<BacktestResultRepository>()
        val client = mockk<RiskOrchestratorClient>()
        coEvery { backtestRepo.findLatestByPortfolioId("port-2") } returns null

        testApplication {
            application { module(frtbRepo, client, backtestRepo) }
            val response = this.client.get("/api/v1/regulatory/backtest/port-2/latest")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /backtest/history returns paginated results") {
        val frtbRepo = mockk<FrtbCalculationRepository>()
        val backtestRepo = mockk<BacktestResultRepository>()
        val client = mockk<RiskOrchestratorClient>()
        coEvery { backtestRepo.findByPortfolioId("port-1", 20, 0) } returns listOf(sampleRecord)

        testApplication {
            application { module(frtbRepo, client, backtestRepo) }
            val response = this.client.get("/api/v1/regulatory/backtest/port-1/history")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "\"results\""
            body shouldContain "\"portfolioId\":\"port-1\""
        }
    }
})
