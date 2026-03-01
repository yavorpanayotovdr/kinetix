package com.kinetix.position.service

import com.kinetix.common.model.*
import com.kinetix.position.model.LimitBreachSeverity
import com.kinetix.position.model.TradeLimits
import com.kinetix.position.persistence.PositionRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")
private val PORTFOLIO = PortfolioId("port-1")
private val AAPL = InstrumentId("AAPL")
private val MSFT = InstrumentId("MSFT")

private fun usd(amount: String) = Money(BigDecimal(amount), USD)

private fun command(
    tradeId: String = "t-1",
    portfolioId: PortfolioId = PORTFOLIO,
    instrumentId: InstrumentId = AAPL,
    assetClass: AssetClass = AssetClass.EQUITY,
    side: Side = Side.BUY,
    quantity: String = "100",
    price: String = "150.00",
    tradedAt: Instant = Instant.parse("2025-01-15T10:00:00Z"),
) = BookTradeCommand(
    tradeId = TradeId(tradeId),
    portfolioId = portfolioId,
    instrumentId = instrumentId,
    assetClass = assetClass,
    side = side,
    quantity = BigDecimal(quantity),
    price = usd(price),
    tradedAt = tradedAt,
)

private fun position(
    portfolioId: PortfolioId = PORTFOLIO,
    instrumentId: InstrumentId = AAPL,
    assetClass: AssetClass = AssetClass.EQUITY,
    quantity: String = "100",
    averageCost: String = "150.00",
    marketPrice: String = "155.00",
) = Position(
    portfolioId = portfolioId,
    instrumentId = instrumentId,
    assetClass = assetClass,
    quantity = BigDecimal(quantity),
    averageCost = usd(averageCost),
    marketPrice = usd(marketPrice),
)

class LimitCheckServiceTest : FunSpec({

    val positionRepo = mockk<PositionRepository>()

    beforeEach {
        clearMocks(positionRepo)
    }

    test("should pass when trade is within all limits") {
        val limits = TradeLimits(
            positionLimit = BigDecimal("1000"),
            notionalLimit = usd("1000000"),
            concentrationLimitPct = 0.25,
        )
        val service = LimitCheckService(positionRepo, limits)

        // Existing position: 100 shares of AAPL. Buying 100 more -> 200 total, well within 1000 limit.
        coEvery { positionRepo.findByKey(PORTFOLIO, AAPL) } returns position(quantity = "100")
        coEvery { positionRepo.findByPortfolioId(PORTFOLIO) } returns listOf(
            position(quantity = "100", marketPrice = "155.00"), // AAPL: mktVal = 15,500
        )

        val result = service.check(command(quantity = "100", price = "150.00"))

        result.breaches.shouldBeEmpty()
        result.blocked shouldBe false
    }

    test("should return hard breach when position limit exceeded") {
        val limits = TradeLimits(
            positionLimit = BigDecimal("500"),
        )
        val service = LimitCheckService(positionRepo, limits)

        // Existing 400 shares, buying 200 more -> 600 which exceeds 500 limit
        coEvery { positionRepo.findByKey(PORTFOLIO, AAPL) } returns position(quantity = "400")
        coEvery { positionRepo.findByPortfolioId(PORTFOLIO) } returns listOf(
            position(quantity = "400", marketPrice = "155.00"),
        )

        val result = service.check(command(quantity = "200", price = "150.00"))

        result.blocked shouldBe true
        result.breaches shouldHaveSize 1
        result.breaches[0].limitType shouldBe "POSITION"
        result.breaches[0].severity shouldBe LimitBreachSeverity.HARD
    }

    test("should return soft breach when approaching position limit") {
        val limits = TradeLimits(
            positionLimit = BigDecimal("500"),
            softLimitPct = 0.8, // soft threshold at 400
        )
        val service = LimitCheckService(positionRepo, limits)

        // Existing 300 shares, buying 120 more -> 420 which is > 400 (80%) but < 500
        coEvery { positionRepo.findByKey(PORTFOLIO, AAPL) } returns position(quantity = "300")
        coEvery { positionRepo.findByPortfolioId(PORTFOLIO) } returns listOf(
            position(quantity = "300", marketPrice = "155.00"),
        )

        val result = service.check(command(quantity = "120", price = "150.00"))

        result.blocked shouldBe false
        result.breaches shouldHaveSize 1
        result.breaches[0].limitType shouldBe "POSITION"
        result.breaches[0].severity shouldBe LimitBreachSeverity.SOFT
    }

    test("should check notional limit against portfolio total exposure") {
        val limits = TradeLimits(
            notionalLimit = usd("100000"), // 100k notional limit
        )
        val service = LimitCheckService(positionRepo, limits)

        // Portfolio has 500 shares of AAPL at $155 = $77,500 market value
        // Trade: buy 200 shares at $150 = $30,000 notional
        // Total exposure: $77,500 + $30,000 = $107,500 > $100,000
        coEvery { positionRepo.findByKey(PORTFOLIO, AAPL) } returns position(quantity = "500", marketPrice = "155.00")
        coEvery { positionRepo.findByPortfolioId(PORTFOLIO) } returns listOf(
            position(quantity = "500", marketPrice = "155.00"),
        )

        val result = service.check(command(quantity = "200", price = "150.00"))

        result.blocked shouldBe true
        result.breaches shouldHaveSize 1
        result.breaches[0].limitType shouldBe "NOTIONAL"
        result.breaches[0].severity shouldBe LimitBreachSeverity.HARD
    }

    test("should check concentration limit as single instrument percentage of portfolio") {
        val limits = TradeLimits(
            concentrationLimitPct = 0.25, // 25% concentration limit
        )
        val service = LimitCheckService(positionRepo, limits)

        // Portfolio: AAPL 100 @ $155 = $15,500 + MSFT 100 @ $300 = $30,000 = total $45,500
        // Trade: buy 200 AAPL @ $150 -> new AAPL mktVal = (100+200) * $155 = $46,500
        // Trade notional = 200 * $150 = $30,000
        // New portfolio total = $45,500 + $30,000 = $75,500
        // AAPL concentration = $46,500 / $75,500 = ~61.6% >> 25%
        coEvery { positionRepo.findByKey(PORTFOLIO, AAPL) } returns position(quantity = "100", marketPrice = "155.00")
        coEvery { positionRepo.findByPortfolioId(PORTFOLIO) } returns listOf(
            position(instrumentId = AAPL, quantity = "100", marketPrice = "155.00"),
            position(instrumentId = MSFT, quantity = "100", averageCost = "290.00", marketPrice = "300.00"),
        )

        val result = service.check(command(quantity = "200", price = "150.00"))

        result.blocked shouldBe true
        result.breaches shouldHaveSize 1
        result.breaches[0].limitType shouldBe "CONCENTRATION"
        result.breaches[0].severity shouldBe LimitBreachSeverity.HARD
    }

    test("should return multiple breaches when multiple limits violated") {
        val limits = TradeLimits(
            positionLimit = BigDecimal("500"),
            notionalLimit = usd("50000"),
        )
        val service = LimitCheckService(positionRepo, limits)

        // Existing 400 shares, buying 200 more -> 600 > 500 position limit
        // Portfolio mktVal = 400 * 155 = 62,000; trade notional = 200 * 150 = 30,000
        // Total exposure = 62,000 + 30,000 = 92,000 > 50,000 notional limit
        coEvery { positionRepo.findByKey(PORTFOLIO, AAPL) } returns position(quantity = "400", marketPrice = "155.00")
        coEvery { positionRepo.findByPortfolioId(PORTFOLIO) } returns listOf(
            position(quantity = "400", marketPrice = "155.00"),
        )

        val result = service.check(command(quantity = "200", price = "150.00"))

        result.blocked shouldBe true
        result.breaches shouldHaveSize 2
        result.breaches.map { it.limitType }.toSet() shouldBe setOf("POSITION", "NOTIONAL")
    }

    test("should pass when no limits are configured") {
        val limits = TradeLimits() // all nulls = no limits
        val service = LimitCheckService(positionRepo, limits)

        coEvery { positionRepo.findByKey(PORTFOLIO, AAPL) } returns position(quantity = "100")
        coEvery { positionRepo.findByPortfolioId(PORTFOLIO) } returns listOf(
            position(quantity = "100", marketPrice = "155.00"),
        )

        val result = service.check(command(quantity = "100", price = "150.00"))

        result.breaches.shouldBeEmpty()
        result.blocked shouldBe false
    }
})
