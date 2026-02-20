package com.kinetix.common.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")
private val PORTFOLIO_ID = PortfolioId("port-1")
private val AAPL = InstrumentId("AAPL")
private val MSFT = InstrumentId("MSFT")

private fun usd(amount: String) = Money(BigDecimal(amount), USD)

private fun trade(
    instrumentId: InstrumentId = AAPL,
    side: Side = Side.BUY,
    quantity: String = "100",
    price: String = "150.00",
    portfolioId: PortfolioId = PORTFOLIO_ID,
) = Trade(
    tradeId = TradeId("t-${System.nanoTime()}"),
    portfolioId = portfolioId,
    instrumentId = instrumentId,
    assetClass = AssetClass.EQUITY,
    side = side,
    quantity = BigDecimal(quantity),
    price = usd(price),
    tradedAt = Instant.now(),
)

class PortfolioTest : FunSpec({

    test("create Portfolio with valid fields") {
        val p = Portfolio(id = PORTFOLIO_ID, name = "Tech Portfolio")
        p.id shouldBe PORTFOLIO_ID
        p.name shouldBe "Tech Portfolio"
    }

    test("Portfolio with blank name throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> { Portfolio(id = PORTFOLIO_ID, name = "") }
        shouldThrow<IllegalArgumentException> { Portfolio(id = PORTFOLIO_ID, name = "   ") }
    }

    test("new Portfolio has empty positions") {
        val p = Portfolio(id = PORTFOLIO_ID, name = "Empty")
        p.positions.shouldBeEmpty()
    }

    // Book trade

    test("book first trade creates new position") {
        val p = Portfolio(id = PORTFOLIO_ID, name = "Tech")
            .bookTrade(trade(instrumentId = AAPL, side = Side.BUY, quantity = "100", price = "150.00"))

        p.positions shouldHaveSize 1
        val pos = p.positions[AAPL]!!
        pos.quantity shouldBe BigDecimal("100")
        pos.averageCost shouldBe usd("150.00")
    }

    test("book second trade for same instrument updates position") {
        val p = Portfolio(id = PORTFOLIO_ID, name = "Tech")
            .bookTrade(trade(instrumentId = AAPL, quantity = "100", price = "150.00"))
            .bookTrade(trade(instrumentId = AAPL, quantity = "50", price = "156.00"))

        p.positions shouldHaveSize 1
        p.positions[AAPL]!!.quantity shouldBe BigDecimal("150")
    }

    test("book trade for different instrument creates second position") {
        val p = Portfolio(id = PORTFOLIO_ID, name = "Tech")
            .bookTrade(trade(instrumentId = AAPL, quantity = "100", price = "150.00"))
            .bookTrade(trade(instrumentId = MSFT, quantity = "50", price = "400.00"))

        p.positions shouldHaveSize 2
    }

    test("book trade with mismatched portfolioId throws IllegalArgumentException") {
        val p = Portfolio(id = PORTFOLIO_ID, name = "Tech")
        shouldThrow<IllegalArgumentException> {
            p.bookTrade(trade(portfolioId = PortfolioId("other")))
        }
    }

    // Total market value

    test("totalMarketValue sums all positions in given currency") {
        val p = Portfolio(id = PORTFOLIO_ID, name = "Tech")
            .bookTrade(trade(instrumentId = AAPL, quantity = "100", price = "150.00"))
            .bookTrade(trade(instrumentId = MSFT, quantity = "50", price = "400.00"))
            .markToMarket(AAPL, usd("155.00"))
            .markToMarket(MSFT, usd("410.00"))

        // AAPL: 100 * 155 = 15500, MSFT: 50 * 410 = 20500
        p.totalMarketValue(USD) shouldBe usd("36000.00")
    }

    test("totalMarketValue of empty portfolio is zero") {
        val p = Portfolio(id = PORTFOLIO_ID, name = "Empty")
        p.totalMarketValue(USD) shouldBe Money.zero(USD)
    }

    // Total unrealized P&L

    test("totalUnrealizedPnl sums all position P&Ls in given currency") {
        val p = Portfolio(id = PORTFOLIO_ID, name = "Tech")
            .bookTrade(trade(instrumentId = AAPL, quantity = "100", price = "150.00"))
            .bookTrade(trade(instrumentId = MSFT, quantity = "50", price = "400.00"))
            .markToMarket(AAPL, usd("155.00"))
            .markToMarket(MSFT, usd("390.00"))

        // AAPL: (155-150)*100 = 500, MSFT: (390-400)*50 = -500
        p.totalUnrealizedPnl(USD) shouldBe usd("0.00")
    }

    test("totalUnrealizedPnl of empty portfolio is zero") {
        val p = Portfolio(id = PORTFOLIO_ID, name = "Empty")
        p.totalUnrealizedPnl(USD) shouldBe Money.zero(USD)
    }

    // Mark-to-market

    test("markToMarket updates specific position") {
        val p = Portfolio(id = PORTFOLIO_ID, name = "Tech")
            .bookTrade(trade(instrumentId = AAPL, quantity = "100", price = "150.00"))
            .markToMarket(AAPL, usd("160.00"))

        p.positions[AAPL]!!.marketPrice shouldBe usd("160.00")
    }

    test("markToMarket for unknown instrument returns same portfolio") {
        val p = Portfolio(id = PORTFOLIO_ID, name = "Tech")
        val same = p.markToMarket(AAPL, usd("160.00"))
        same shouldBe p
    }

    // Integration scenario

    test("full scenario: buy, buy more, sell partial, mark to market, check P&L") {
        val p = Portfolio(id = PORTFOLIO_ID, name = "Tech")
            // Buy 100 AAPL @ 150
            .bookTrade(trade(instrumentId = AAPL, side = Side.BUY, quantity = "100", price = "150.00"))
            // Buy 50 more AAPL @ 156 => 150 shares, avgCost = (100*150 + 50*156)/150 = 152
            .bookTrade(trade(instrumentId = AAPL, side = Side.BUY, quantity = "50", price = "156.00"))
            // Sell 30 AAPL @ 160 => 120 shares, avgCost stays 152
            .bookTrade(trade(instrumentId = AAPL, side = Side.SELL, quantity = "30", price = "160.00"))
            // Market price moves to 165
            .markToMarket(AAPL, usd("165.00"))

        val pos = p.positions[AAPL]!!
        pos.quantity shouldBe BigDecimal("120")
        pos.averageCost.amount.toDouble() shouldBe 152.0
        pos.marketPrice shouldBe usd("165.00")
        // Unrealized P&L = (165 - 152) * 120 = 1560
        pos.unrealizedPnl shouldBe usd("1560.00")
        // Market value = 165 * 120 = 19800
        pos.marketValue shouldBe usd("19800.00")
    }
})
