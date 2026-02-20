package com.kinetix.common.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")
private val AAPL = InstrumentId("AAPL")
private fun usd(amount: String) = Money(BigDecimal(amount), USD)

private fun marketDataPoint(
    instrumentId: InstrumentId = AAPL,
    price: Money = usd("150.00"),
    timestamp: Instant = Instant.parse("2025-06-15T14:30:00Z"),
    source: MarketDataSource = MarketDataSource.BLOOMBERG,
) = MarketDataPoint(
    instrumentId = instrumentId,
    price = price,
    timestamp = timestamp,
    source = source,
)

class MarketDataPointTest : FunSpec({

    test("create MarketDataPoint with valid fields") {
        val point = marketDataPoint()
        point.instrumentId shouldBe AAPL
        point.price shouldBe usd("150.00")
        point.timestamp shouldBe Instant.parse("2025-06-15T14:30:00Z")
        point.source shouldBe MarketDataSource.BLOOMBERG
    }

    test("MarketDataPoint with zero price is allowed") {
        val point = marketDataPoint(price = usd("0.00"))
        point.price.amount.compareTo(BigDecimal.ZERO) shouldBe 0
    }

    test("MarketDataPoint with negative price throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            marketDataPoint(price = usd("-1.00"))
        }
    }

    test("equal MarketDataPoints are equal") {
        marketDataPoint() shouldBe marketDataPoint()
    }

    test("MarketDataPoints with different timestamps are not equal") {
        marketDataPoint(timestamp = Instant.parse("2025-06-15T14:30:00Z")) shouldNotBe
            marketDataPoint(timestamp = Instant.parse("2025-06-15T15:00:00Z"))
    }

    test("MarketDataPoints with different sources are not equal") {
        marketDataPoint(source = MarketDataSource.BLOOMBERG) shouldNotBe
            marketDataPoint(source = MarketDataSource.REUTERS)
    }

    test("MarketDataSource enum has all expected values") {
        MarketDataSource.entries.map { it.name } shouldBe
            listOf("BLOOMBERG", "REUTERS", "EXCHANGE", "INTERNAL", "MANUAL")
    }

    test("MarketDataSource display names are human-readable") {
        MarketDataSource.BLOOMBERG.displayName shouldBe "Bloomberg"
        MarketDataSource.REUTERS.displayName shouldBe "Reuters"
        MarketDataSource.EXCHANGE.displayName shouldBe "Exchange"
        MarketDataSource.INTERNAL.displayName shouldBe "Internal"
        MarketDataSource.MANUAL.displayName shouldBe "Manual"
    }

    test("MarketDataPoint price is compatible with Position.markToMarket") {
        val point = marketDataPoint(price = usd("155.00"))
        val position = Position.empty(
            portfolioId = PortfolioId("p1"),
            instrumentId = AAPL,
            assetClass = AssetClass.EQUITY,
            currency = USD,
        )
        val updated = position.markToMarket(point.price)
        updated.marketPrice shouldBe usd("155.00")
    }
})
