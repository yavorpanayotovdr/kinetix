package com.kinetix.risk.service

import com.kinetix.common.model.*
import com.kinetix.risk.client.PriceServiceClient
import com.kinetix.risk.model.DiscoveredDependency
import com.kinetix.risk.model.ScalarMarketData
import com.kinetix.risk.model.TimeSeriesMarketData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")

private fun pricePoint(
    instrumentId: String = "AAPL",
    amount: String = "170.50",
    timestamp: Instant = Instant.parse("2026-02-24T10:00:00Z"),
) = PricePoint(
    instrumentId = InstrumentId(instrumentId),
    price = Money(BigDecimal(amount), USD),
    timestamp = timestamp,
    source = PriceSource.EXCHANGE,
)

class MarketDataFetcherTest : FunSpec({

    val priceServiceClient = mockk<PriceServiceClient>()
    val fetcher = MarketDataFetcher(priceServiceClient)

    beforeEach {
        clearMocks(priceServiceClient)
    }

    test("fetches spot price from price service") {
        val deps = listOf(
            DiscoveredDependency("SPOT_PRICE", "AAPL", "EQUITY"),
        )

        coEvery { priceServiceClient.getLatestPrice(InstrumentId("AAPL")) } returns pricePoint()

        val result = fetcher.fetch(deps)

        result shouldHaveSize 1
        val scalar = result[0].shouldBeInstanceOf<ScalarMarketData>()
        scalar.dataType shouldBe "SPOT_PRICE"
        scalar.instrumentId shouldBe "AAPL"
        scalar.value shouldBe 170.50
    }

    test("fetches historical prices from price service") {
        val deps = listOf(
            DiscoveredDependency("HISTORICAL_PRICES", "AAPL", "EQUITY", mapOf("lookbackDays" to "252")),
        )

        coEvery { priceServiceClient.getPriceHistory(InstrumentId("AAPL"), any(), any()) } returns listOf(
            pricePoint(amount = "168.00", timestamp = Instant.parse("2026-02-22T10:00:00Z")),
            pricePoint(amount = "170.50", timestamp = Instant.parse("2026-02-23T10:00:00Z")),
        )

        val result = fetcher.fetch(deps)

        result shouldHaveSize 1
        val ts = result[0].shouldBeInstanceOf<TimeSeriesMarketData>()
        ts.dataType shouldBe "HISTORICAL_PRICES"
        ts.points shouldHaveSize 2
    }

    test("skips unfetchable data types like volatility surface") {
        val deps = listOf(
            DiscoveredDependency("SPOT_PRICE", "AAPL", "EQUITY"),
            DiscoveredDependency("VOLATILITY_SURFACE", "AAPL", "EQUITY"),
            DiscoveredDependency("YIELD_CURVE", "AAPL", "EQUITY"),
        )

        coEvery { priceServiceClient.getLatestPrice(InstrumentId("AAPL")) } returns pricePoint()

        val result = fetcher.fetch(deps)

        result shouldHaveSize 1
        result[0].shouldBeInstanceOf<ScalarMarketData>()
    }

    test("continues fetching remaining dependencies when one fails") {
        val deps = listOf(
            DiscoveredDependency("SPOT_PRICE", "FAIL", "EQUITY"),
            DiscoveredDependency("SPOT_PRICE", "AAPL", "EQUITY"),
        )

        coEvery { priceServiceClient.getLatestPrice(InstrumentId("FAIL")) } throws RuntimeException("price unavailable")
        coEvery { priceServiceClient.getLatestPrice(InstrumentId("AAPL")) } returns pricePoint()

        val result = fetcher.fetch(deps)

        result shouldHaveSize 1
        result[0].instrumentId shouldBe "AAPL"
    }

    test("returns empty list when given no dependencies") {
        val result = fetcher.fetch(emptyList())

        result.shouldBeEmpty()
    }
})
