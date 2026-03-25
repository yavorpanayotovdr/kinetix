package com.kinetix.risk.service

import com.kinetix.common.model.BookId
import com.kinetix.risk.model.IntradayVaRPoint
import com.kinetix.risk.model.TradeAnnotation
import com.kinetix.risk.persistence.IntradayVaRTimelineRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant

private val BOOK = BookId("book-1")
private val FROM = Instant.parse("2026-03-25T08:00:00Z")
private val TO = Instant.parse("2026-03-25T16:00:00Z")

private fun varPoint(
    ts: String,
    varValue: Double = 100_000.0,
    es: Double = 120_000.0,
    delta: Double? = 0.5,
    gamma: Double? = 0.01,
    vega: Double? = 200.0,
): IntradayVaRPoint = IntradayVaRPoint(
    timestamp = Instant.parse(ts),
    varValue = varValue,
    expectedShortfall = es,
    delta = delta,
    gamma = gamma,
    vega = vega,
)

private fun tradeAnnotation(
    ts: String,
    instrumentId: String = "AAPL",
    side: String = "BUY",
    quantity: String = "100",
    tradeId: String = "trade-1",
): TradeAnnotation = TradeAnnotation(
    timestamp = Instant.parse(ts),
    instrumentId = instrumentId,
    side = side,
    quantity = quantity,
    tradeId = tradeId,
)

class IntradayVaRTimelineServiceTest : FunSpec({

    val repository = mockk<IntradayVaRTimelineRepository>()
    val tradeProvider = mockk<IntradayVaRTradeProvider>()
    val service = IntradayVaRTimelineService(repository, tradeProvider)

    test("returns empty timeline when no VaR points in range") {
        coEvery { repository.findInRange(BOOK, FROM, TO) } returns emptyList()
        coEvery { tradeProvider.getTradesInRange(BOOK, FROM, TO) } returns emptyList()

        val result = service.getTimeline(BOOK, FROM, TO)

        result.varPoints shouldHaveSize 0
        result.tradeAnnotations shouldHaveSize 0
    }

    test("returns VaR points ordered by timestamp ascending") {
        coEvery { repository.findInRange(BOOK, FROM, TO) } returns listOf(
            varPoint("2026-03-25T10:00:00Z", varValue = 200_000.0),
            varPoint("2026-03-25T09:00:00Z", varValue = 150_000.0),
            varPoint("2026-03-25T11:00:00Z", varValue = 250_000.0),
        )
        coEvery { tradeProvider.getTradesInRange(BOOK, FROM, TO) } returns emptyList()

        val result = service.getTimeline(BOOK, FROM, TO)

        result.varPoints shouldHaveSize 3
        result.varPoints[0].timestamp shouldBe Instant.parse("2026-03-25T09:00:00Z")
        result.varPoints[1].timestamp shouldBe Instant.parse("2026-03-25T10:00:00Z")
        result.varPoints[2].timestamp shouldBe Instant.parse("2026-03-25T11:00:00Z")
    }

    test("maps VaR point fields correctly") {
        coEvery { repository.findInRange(BOOK, FROM, TO) } returns listOf(
            varPoint(
                "2026-03-25T09:00:00Z",
                varValue = 123_456.78,
                es = 145_000.00,
                delta = 0.72,
                gamma = 0.03,
                vega = 350.0,
            ),
        )
        coEvery { tradeProvider.getTradesInRange(BOOK, FROM, TO) } returns emptyList()

        val result = service.getTimeline(BOOK, FROM, TO)

        val point = result.varPoints[0]
        point.varValue shouldBe 123_456.78
        point.expectedShortfall shouldBe 145_000.00
        point.delta shouldBe 0.72
        point.gamma shouldBe 0.03
        point.vega shouldBe 350.0
    }

    test("includes trade annotations from position service") {
        coEvery { repository.findInRange(BOOK, FROM, TO) } returns listOf(
            varPoint("2026-03-25T09:00:00Z"),
        )
        coEvery { tradeProvider.getTradesInRange(BOOK, FROM, TO) } returns listOf(
            tradeAnnotation("2026-03-25T09:15:00Z", instrumentId = "AAPL", side = "BUY", quantity = "100", tradeId = "t-1"),
            tradeAnnotation("2026-03-25T10:30:00Z", instrumentId = "MSFT", side = "SELL", quantity = "50", tradeId = "t-2"),
        )

        val result = service.getTimeline(BOOK, FROM, TO)

        result.tradeAnnotations shouldHaveSize 2
        result.tradeAnnotations[0].tradeId shouldBe "t-1"
        result.tradeAnnotations[0].instrumentId shouldBe "AAPL"
        result.tradeAnnotations[0].side shouldBe "BUY"
        result.tradeAnnotations[0].quantity shouldBe "100"
        result.tradeAnnotations[1].tradeId shouldBe "t-2"
    }

    test("trade annotations are ordered by timestamp ascending") {
        coEvery { repository.findInRange(BOOK, FROM, TO) } returns emptyList()
        coEvery { tradeProvider.getTradesInRange(BOOK, FROM, TO) } returns listOf(
            tradeAnnotation("2026-03-25T14:00:00Z", tradeId = "late"),
            tradeAnnotation("2026-03-25T08:30:00Z", tradeId = "early"),
            tradeAnnotation("2026-03-25T11:00:00Z", tradeId = "mid"),
        )

        val result = service.getTimeline(BOOK, FROM, TO)

        result.tradeAnnotations[0].tradeId shouldBe "early"
        result.tradeAnnotations[1].tradeId shouldBe "mid"
        result.tradeAnnotations[2].tradeId shouldBe "late"
    }

    test("handles null greeks gracefully") {
        coEvery { repository.findInRange(BOOK, FROM, TO) } returns listOf(
            varPoint("2026-03-25T09:00:00Z", delta = null, gamma = null, vega = null),
        )
        coEvery { tradeProvider.getTradesInRange(BOOK, FROM, TO) } returns emptyList()

        val result = service.getTimeline(BOOK, FROM, TO)

        val point = result.varPoints[0]
        point.delta shouldBe null
        point.gamma shouldBe null
        point.vega shouldBe null
    }
})
