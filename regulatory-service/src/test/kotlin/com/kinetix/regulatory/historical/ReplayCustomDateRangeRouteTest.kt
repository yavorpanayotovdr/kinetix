package com.kinetix.regulatory.historical

import com.kinetix.regulatory.client.PriceServiceClient
import com.kinetix.regulatory.client.RiskOrchestratorClient
import com.kinetix.regulatory.historical.dto.ReplayResultResponse
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

class ReplayCustomDateRangeRouteTest : FunSpec({

    val riskClient = mockk<RiskOrchestratorClient>()
    val priceClient = mockk<PriceServiceClient>()
    val historicalRepo = mockk<HistoricalScenarioRepository>()

    test("POST /api/v1/historical-scenarios/custom-replay returns 200 with replay result") {
        coEvery {
            priceClient.fetchDailyClosePrices("AAPL", "2024-01-02T00:00:00Z", "2024-01-05T00:00:00Z")
        } returns listOf(
            DailyClosePrice("AAPL", "2024-01-02", 180.0),
            DailyClosePrice("AAPL", "2024-01-03", 185.0),
            DailyClosePrice("AAPL", "2024-01-04", 178.0),
        )
        coEvery {
            riskClient.runHistoricalReplay(any(), any(), any(), any())
        } returns ReplayResultResponse(
            periodId = "CUSTOM",
            scenarioName = "Custom Date Range",
            bookId = "BOOK-1",
            totalPnlImpact = "-5200.00",
            positionImpacts = emptyList(),
            windowStart = "2024-01-02",
            windowEnd = "2024-01-05",
            calculatedAt = "2026-03-26T10:00:00Z",
        )

        testApplication {
            application {
                module(
                    repository = mockk<FrtbCalculationRepository>(),
                    client = riskClient,
                    historicalScenarioRepository = historicalRepo,
                    priceServiceClient = priceClient,
                )
            }
            val response = client.post("/api/v1/historical-scenarios/custom-replay") {
                contentType(ContentType.Application.Json)
                setBody("""{"bookId":"BOOK-1","instrumentIds":["AAPL"],"startDate":"2024-01-02","endDate":"2024-01-05"}""")
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "\"periodId\":\"CUSTOM\""
            body shouldContain "\"bookId\":\"BOOK-1\""
            body shouldContain "\"totalPnlImpact\":\"-5200.00\""
        }
    }

    test("POST /api/v1/historical-scenarios/custom-replay returns 400 when start date equals end date") {
        testApplication {
            application {
                module(
                    repository = mockk<FrtbCalculationRepository>(),
                    client = riskClient,
                    historicalScenarioRepository = historicalRepo,
                    priceServiceClient = priceClient,
                )
            }
            val response = client.post("/api/v1/historical-scenarios/custom-replay") {
                contentType(ContentType.Application.Json)
                setBody("""{"bookId":"BOOK-1","instrumentIds":["AAPL"],"startDate":"2024-01-02","endDate":"2024-01-02"}""")
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /api/v1/historical-scenarios/custom-replay returns 400 when start date is after end date") {
        testApplication {
            application {
                module(
                    repository = mockk<FrtbCalculationRepository>(),
                    client = riskClient,
                    historicalScenarioRepository = historicalRepo,
                    priceServiceClient = priceClient,
                )
            }
            val response = client.post("/api/v1/historical-scenarios/custom-replay") {
                contentType(ContentType.Application.Json)
                setBody("""{"bookId":"BOOK-1","instrumentIds":["AAPL"],"startDate":"2024-01-10","endDate":"2024-01-02"}""")
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
