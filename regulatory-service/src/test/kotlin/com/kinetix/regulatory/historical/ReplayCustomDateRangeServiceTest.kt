package com.kinetix.regulatory.historical

import com.kinetix.regulatory.client.PriceServiceClient
import com.kinetix.regulatory.client.RiskOrchestratorClient
import com.kinetix.regulatory.historical.dto.PositionReplayImpact
import com.kinetix.regulatory.historical.dto.ReplayResultResponse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class ReplayCustomDateRangeServiceTest : FunSpec({

    val priceServiceClient = mockk<PriceServiceClient>()
    val riskOrchestratorClient = mockk<RiskOrchestratorClient>()
    val repository = mockk<HistoricalScenarioRepository>()
    val service = HistoricalReplayService(repository, riskOrchestratorClient, priceServiceClient)

    beforeTest {
        clearMocks(priceServiceClient, riskOrchestratorClient, repository)
    }

    test("rejects request when start date is not before end date") {
        shouldThrow<IllegalArgumentException> {
            service.replayCustomDateRange(
                bookId = "BOOK-1",
                instrumentIds = listOf("AAPL"),
                startDate = "2024-01-10",
                endDate = "2024-01-10",
            )
        }
    }

    test("rejects request when start date is after end date") {
        shouldThrow<IllegalArgumentException> {
            service.replayCustomDateRange(
                bookId = "BOOK-1",
                instrumentIds = listOf("AAPL"),
                startDate = "2024-01-15",
                endDate = "2024-01-10",
            )
        }
    }

    test("fetches price history from price-service and derives daily returns for each instrument") {
        // Two prices for AAPL: on day 1 price=100, day 2 price=105 -> return = ln(105/100) ≈ 0.04879
        coEvery {
            priceServiceClient.fetchDailyClosePrices("AAPL", "2024-01-02T00:00:00Z", "2024-01-04T00:00:00Z")
        } returns listOf(
            DailyClosePrice("AAPL", "2024-01-02", 100.0),
            DailyClosePrice("AAPL", "2024-01-03", 105.0),
        )
        coEvery {
            riskOrchestratorClient.runHistoricalReplay(any(), any(), any(), any())
        } returns aReplayResultResponse("2024-01-02", "2024-01-03")

        service.replayCustomDateRange(
            bookId = "BOOK-1",
            instrumentIds = listOf("AAPL"),
            startDate = "2024-01-02",
            endDate = "2024-01-04",
        )

        coVerify(exactly = 1) {
            priceServiceClient.fetchDailyClosePrices("AAPL", "2024-01-02T00:00:00Z", "2024-01-04T00:00:00Z")
        }
    }

    test("passes derived instrument returns to the risk-orchestrator") {
        coEvery {
            priceServiceClient.fetchDailyClosePrices("AAPL", any(), any())
        } returns listOf(
            DailyClosePrice("AAPL", "2024-01-02", 100.0),
            DailyClosePrice("AAPL", "2024-01-03", 110.0),
        )

        var capturedReturns: Map<String, List<Double>>? = null
        coEvery {
            riskOrchestratorClient.runHistoricalReplay(any(), any(), any(), any())
        } answers {
            @Suppress("UNCHECKED_CAST")
            capturedReturns = secondArg<Map<String, List<Double>>>()
            aReplayResultResponse("2024-01-02", "2024-01-03")
        }

        service.replayCustomDateRange(
            bookId = "BOOK-1",
            instrumentIds = listOf("AAPL"),
            startDate = "2024-01-02",
            endDate = "2024-01-04",
        )

        val aaplReturns = capturedReturns!!["AAPL"]!!
        aaplReturns.size shouldBe 1
        // ln(110/100) = ln(1.1) ≈ 0.09531
        val expected = Math.log(110.0 / 100.0)
        aaplReturns[0].shouldBeCloseTo(expected, delta = 0.00001)
    }

    test("passes start and end dates to the risk-orchestrator as window bounds") {
        coEvery {
            priceServiceClient.fetchDailyClosePrices(any(), any(), any())
        } returns listOf(
            DailyClosePrice("MSFT", "2024-01-02", 380.0),
            DailyClosePrice("MSFT", "2024-01-03", 375.0),
        )
        coEvery {
            riskOrchestratorClient.runHistoricalReplay(any(), any(), any(), any())
        } returns aReplayResultResponse("2024-01-02", "2024-01-03")

        service.replayCustomDateRange(
            bookId = "BOOK-1",
            instrumentIds = listOf("MSFT"),
            startDate = "2024-01-02",
            endDate = "2024-01-04",
        )

        coVerify(exactly = 1) {
            riskOrchestratorClient.runHistoricalReplay("BOOK-1", any(), "2024-01-02", "2024-01-04")
        }
    }

    test("omits instruments with fewer than two price points from the returns map") {
        coEvery {
            priceServiceClient.fetchDailyClosePrices("AAPL", any(), any())
        } returns listOf(
            DailyClosePrice("AAPL", "2024-01-02", 100.0),
            DailyClosePrice("AAPL", "2024-01-03", 105.0),
        )
        coEvery {
            priceServiceClient.fetchDailyClosePrices("RARE", any(), any())
        } returns listOf(
            DailyClosePrice("RARE", "2024-01-02", 50.0),
            // only one point — cannot compute returns
        )

        var capturedReturns: Map<String, List<Double>>? = null
        coEvery {
            riskOrchestratorClient.runHistoricalReplay(any(), any(), any(), any())
        } answers {
            @Suppress("UNCHECKED_CAST")
            capturedReturns = secondArg<Map<String, List<Double>>>()
            aReplayResultResponse("2024-01-02", "2024-01-03")
        }

        service.replayCustomDateRange(
            bookId = "BOOK-1",
            instrumentIds = listOf("AAPL", "RARE"),
            startDate = "2024-01-02",
            endDate = "2024-01-04",
        )

        capturedReturns!!.containsKey("AAPL") shouldBe true
        capturedReturns!!.containsKey("RARE") shouldBe false
    }

    test("fetches price history for each instrument independently") {
        val instrumentIds = listOf("AAPL", "MSFT")
        for (id in instrumentIds) {
            coEvery {
                priceServiceClient.fetchDailyClosePrices(id, any(), any())
            } returns listOf(
                DailyClosePrice(id, "2024-01-02", 100.0),
                DailyClosePrice(id, "2024-01-03", 102.0),
            )
        }
        coEvery {
            riskOrchestratorClient.runHistoricalReplay(any(), any(), any(), any())
        } returns aReplayResultResponse("2024-01-02", "2024-01-03")

        service.replayCustomDateRange(
            bookId = "BOOK-1",
            instrumentIds = instrumentIds,
            startDate = "2024-01-02",
            endDate = "2024-01-04",
        )

        coVerify(exactly = 1) { priceServiceClient.fetchDailyClosePrices("AAPL", any(), any()) }
        coVerify(exactly = 1) { priceServiceClient.fetchDailyClosePrices("MSFT", any(), any()) }
    }
})

private fun Double.shouldBeCloseTo(expected: Double, delta: Double) {
    val diff = Math.abs(this - expected)
    if (diff > delta) {
        throw AssertionError("Expected $this to be within $delta of $expected, but difference was $diff")
    }
}

private fun aReplayResultResponse(windowStart: String, windowEnd: String) = ReplayResultResponse(
    periodId = "CUSTOM",
    scenarioName = "Custom Date Range",
    bookId = "BOOK-1",
    totalPnlImpact = "0.00",
    positionImpacts = emptyList(),
    windowStart = windowStart,
    windowEnd = windowEnd,
    calculatedAt = "2026-03-26T10:00:00Z",
)
