package com.kinetix.position.service

import com.kinetix.common.model.*
import com.kinetix.position.persistence.PositionRepository
import com.kinetix.position.persistence.TradeEventRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")
private val PORTFOLIO = PortfolioId("port-1")
private val AAPL = InstrumentId("AAPL")

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

private val noOpTransaction = object : TransactionalRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

class TradeBookingServiceTest : FunSpec({

    val tradeRepo = mockk<TradeEventRepository>()
    val positionRepo = mockk<PositionRepository>()
    val service = TradeBookingService(tradeRepo, positionRepo, noOpTransaction)

    beforeEach {
        clearMocks(tradeRepo, positionRepo)
    }

    test("books a new trade and creates position from empty") {
        coEvery { tradeRepo.findByTradeId(TradeId("t-1")) } returns null
        coEvery { tradeRepo.save(any()) } just runs
        coEvery { positionRepo.findByKey(PORTFOLIO, AAPL) } returns null
        coEvery { positionRepo.save(any()) } just runs

        val result = service.handle(command())

        result.trade.tradeId shouldBe TradeId("t-1")
        result.trade.side shouldBe Side.BUY
        result.position.quantity.compareTo(BigDecimal("100")) shouldBe 0
        result.position.averageCost shouldBe usd("150.00")

        coVerify(exactly = 1) { tradeRepo.save(any()) }
        coVerify(exactly = 1) { positionRepo.save(any()) }
    }

    test("books a trade and updates existing position with weighted average cost") {
        val existing = position(quantity = "100", averageCost = "140.00", marketPrice = "155.00")

        coEvery { tradeRepo.findByTradeId(TradeId("t-1")) } returns null
        coEvery { tradeRepo.save(any()) } just runs
        coEvery { positionRepo.findByKey(PORTFOLIO, AAPL) } returns existing
        coEvery { positionRepo.save(any()) } just runs

        val result = service.handle(command())

        // 100 @ 140 + 100 @ 150 = 200 @ 145
        result.position.quantity.compareTo(BigDecimal("200")) shouldBe 0
        result.position.averageCost.amount.compareTo(BigDecimal("145")) shouldBe 0
    }

    test("duplicate tradeId returns existing state without re-saving") {
        val existingTrade = Trade(
            tradeId = TradeId("t-1"),
            portfolioId = PORTFOLIO,
            instrumentId = AAPL,
            assetClass = AssetClass.EQUITY,
            side = Side.BUY,
            quantity = BigDecimal("100"),
            price = usd("150.00"),
            tradedAt = Instant.parse("2025-01-15T10:00:00Z"),
        )
        val existingPosition = position()

        coEvery { tradeRepo.findByTradeId(TradeId("t-1")) } returns existingTrade
        coEvery { positionRepo.findByKey(PORTFOLIO, AAPL) } returns existingPosition

        val result = service.handle(command())

        result.trade shouldBe existingTrade
        result.position shouldBe existingPosition

        coVerify(exactly = 0) { tradeRepo.save(any()) }
        coVerify(exactly = 0) { positionRepo.save(any()) }
    }

    test("SELL trade reduces existing position") {
        val existing = position(quantity = "100", averageCost = "150.00", marketPrice = "155.00")

        coEvery { tradeRepo.findByTradeId(any()) } returns null
        coEvery { tradeRepo.save(any()) } just runs
        coEvery { positionRepo.findByKey(PORTFOLIO, AAPL) } returns existing
        coEvery { positionRepo.save(any()) } just runs

        val result = service.handle(command(side = Side.SELL, quantity = "30"))

        result.position.quantity.compareTo(BigDecimal("70")) shouldBe 0
        result.position.averageCost shouldBe usd("150.00") // cost unchanged on partial close
    }

    test("saves the position returned by applyTrade") {
        coEvery { tradeRepo.findByTradeId(any()) } returns null
        coEvery { tradeRepo.save(any()) } just runs
        coEvery { positionRepo.findByKey(any(), any()) } returns null
        val savedPosition = slot<Position>()
        coEvery { positionRepo.save(capture(savedPosition)) } just runs

        service.handle(command(quantity = "50", price = "200.00"))

        savedPosition.captured.quantity.compareTo(BigDecimal("50")) shouldBe 0
        savedPosition.captured.averageCost shouldBe usd("200.00")
    }
})
