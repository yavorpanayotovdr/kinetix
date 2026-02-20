package com.kinetix.common.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")
private val EUR = Currency.getInstance("EUR")
private val PORTFOLIO = PortfolioId("port-1")
private val AAPL = InstrumentId("AAPL")

private fun usd(amount: String) = Money(BigDecimal(amount), USD)

private fun position(
    quantity: String = "100",
    averageCost: String = "50.00",
    marketPrice: String = "55.00",
) = Position(
    portfolioId = PORTFOLIO,
    instrumentId = AAPL,
    assetClass = AssetClass.EQUITY,
    quantity = BigDecimal(quantity),
    averageCost = usd(averageCost),
    marketPrice = usd(marketPrice),
)

private fun buyTrade(
    quantity: String = "100",
    price: String = "50.00",
    instrumentId: InstrumentId = AAPL,
    portfolioId: PortfolioId = PORTFOLIO,
) = Trade(
    tradeId = TradeId("t-${System.nanoTime()}"),
    portfolioId = portfolioId,
    instrumentId = instrumentId,
    assetClass = AssetClass.EQUITY,
    side = Side.BUY,
    quantity = BigDecimal(quantity),
    price = usd(price),
    tradedAt = Instant.now(),
)

private fun sellTrade(
    quantity: String = "100",
    price: String = "55.00",
    instrumentId: InstrumentId = AAPL,
    portfolioId: PortfolioId = PORTFOLIO,
) = Trade(
    tradeId = TradeId("t-${System.nanoTime()}"),
    portfolioId = portfolioId,
    instrumentId = instrumentId,
    assetClass = AssetClass.EQUITY,
    side = Side.SELL,
    quantity = BigDecimal(quantity),
    price = usd(price),
    tradedAt = Instant.now(),
)

class PositionTest : FunSpec({

    test("create Position with valid fields") {
        val pos = position()
        pos.portfolioId shouldBe PORTFOLIO
        pos.instrumentId shouldBe AAPL
        pos.assetClass shouldBe AssetClass.EQUITY
        pos.currency shouldBe USD
    }

    test("Position with mismatched currencies throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            Position(
                portfolioId = PORTFOLIO,
                instrumentId = AAPL,
                assetClass = AssetClass.EQUITY,
                quantity = BigDecimal("100"),
                averageCost = usd("50.00"),
                marketPrice = Money(BigDecimal("55.00"), EUR),
            )
        }
    }

    // P&L calculations

    test("unrealized P&L for long position with profit") {
        val pos = position(quantity = "100", averageCost = "50.00", marketPrice = "55.00")
        pos.unrealizedPnl shouldBe usd("500.00")
    }

    test("unrealized P&L for long position with loss") {
        val pos = position(quantity = "100", averageCost = "50.00", marketPrice = "45.00")
        pos.unrealizedPnl shouldBe usd("-500.00")
    }

    test("unrealized P&L for short position with profit") {
        val pos = position(quantity = "-100", averageCost = "50.00", marketPrice = "45.00")
        pos.unrealizedPnl shouldBe usd("500.00")
    }

    test("unrealized P&L for short position with loss") {
        val pos = position(quantity = "-100", averageCost = "50.00", marketPrice = "55.00")
        pos.unrealizedPnl shouldBe usd("-500.00")
    }

    test("unrealized P&L for flat position is zero") {
        val pos = position(quantity = "0", averageCost = "50.00", marketPrice = "55.00")
        pos.unrealizedPnl shouldBe usd("0.00")
    }

    // Market value

    test("market value for long position is positive") {
        val pos = position(quantity = "100", marketPrice = "55.00")
        pos.marketValue shouldBe usd("5500.00")
    }

    test("market value for short position is negative") {
        val pos = position(quantity = "-100", marketPrice = "55.00")
        pos.marketValue shouldBe usd("-5500.00")
    }

    test("market value for flat position is zero") {
        val pos = position(quantity = "0", marketPrice = "55.00")
        pos.marketValue shouldBe usd("0.00")
    }

    // Mark-to-market

    test("markToMarket returns new position with updated price") {
        val pos = position(marketPrice = "55.00")
        val updated = pos.markToMarket(usd("60.00"))
        updated.marketPrice shouldBe usd("60.00")
        updated.averageCost shouldBe pos.averageCost
    }

    test("markToMarket with wrong currency throws IllegalArgumentException") {
        val pos = position()
        shouldThrow<IllegalArgumentException> {
            pos.markToMarket(Money(BigDecimal("60.00"), EUR))
        }
    }

    test("markToMarket recalculates unrealized P&L") {
        val pos = position(quantity = "100", averageCost = "50.00", marketPrice = "55.00")
        val updated = pos.markToMarket(usd("60.00"))
        updated.unrealizedPnl shouldBe usd("1000.00")
    }

    // Apply trade — increasing position

    test("apply BUY trade to flat position creates long position") {
        val pos = Position.empty(PORTFOLIO, AAPL, AssetClass.EQUITY, USD)
        val updated = pos.applyTrade(buyTrade(quantity = "100", price = "50.00"))
        updated.quantity shouldBe BigDecimal("100")
        updated.averageCost shouldBe usd("50.00")
    }

    test("apply BUY trade to long position increases quantity and recalculates average cost") {
        // 100 @ 50, buy 50 @ 56 => 150 @ (100*50 + 50*56)/150 = 52
        val pos = position(quantity = "100", averageCost = "50.00", marketPrice = "55.00")
        val updated = pos.applyTrade(buyTrade(quantity = "50", price = "56.00"))
        updated.quantity shouldBe BigDecimal("150")
        updated.averageCost.amount.toDouble() shouldBe 52.0
    }

    test("apply SELL trade to short position increases short and recalculates average cost") {
        // -100 @ 50, sell 50 @ 44 => -150 @ (100*50 + 50*44)/150 = 48
        val pos = position(quantity = "-100", averageCost = "50.00", marketPrice = "45.00")
        val updated = pos.applyTrade(sellTrade(quantity = "50", price = "44.00"))
        updated.quantity shouldBe BigDecimal("-150")
        updated.averageCost.amount.toDouble() shouldBe 48.0
    }

    // Apply trade — reducing position

    test("apply SELL trade to long position reduces quantity, keeps average cost") {
        val pos = position(quantity = "100", averageCost = "50.00", marketPrice = "55.00")
        val updated = pos.applyTrade(sellTrade(quantity = "30", price = "55.00"))
        updated.quantity shouldBe BigDecimal("70")
        updated.averageCost shouldBe usd("50.00")
    }

    test("apply BUY trade to short position reduces short, keeps average cost") {
        val pos = position(quantity = "-100", averageCost = "50.00", marketPrice = "45.00")
        val updated = pos.applyTrade(buyTrade(quantity = "30", price = "45.00"))
        updated.quantity shouldBe BigDecimal("-70")
        updated.averageCost shouldBe usd("50.00")
    }

    // Apply trade — closing position

    test("apply SELL trade that closes long position results in zero quantity") {
        val pos = position(quantity = "100", averageCost = "50.00", marketPrice = "55.00")
        val updated = pos.applyTrade(sellTrade(quantity = "100", price = "55.00"))
        updated.quantity shouldBe BigDecimal("0")
    }

    // Apply trade — flipping position

    test("apply SELL trade that flips long to short uses trade price as new average cost") {
        val pos = position(quantity = "100", averageCost = "50.00", marketPrice = "55.00")
        val updated = pos.applyTrade(sellTrade(quantity = "150", price = "55.00"))
        updated.quantity shouldBe BigDecimal("-50")
        updated.averageCost shouldBe usd("55.00")
    }

    // Validation

    test("apply trade with mismatched portfolioId throws IllegalArgumentException") {
        val pos = position()
        shouldThrow<IllegalArgumentException> {
            pos.applyTrade(buyTrade(portfolioId = PortfolioId("other")))
        }
    }

    test("apply trade with mismatched instrumentId throws IllegalArgumentException") {
        val pos = position()
        shouldThrow<IllegalArgumentException> {
            pos.applyTrade(buyTrade(instrumentId = InstrumentId("MSFT")))
        }
    }

    // Factory

    test("Position.empty creates flat position with zero values") {
        val pos = Position.empty(PORTFOLIO, AAPL, AssetClass.EQUITY, USD)
        pos.quantity shouldBe BigDecimal.ZERO
        pos.averageCost shouldBe Money.zero(USD)
        pos.marketPrice shouldBe Money.zero(USD)
        pos.currency shouldBe USD
    }
})
