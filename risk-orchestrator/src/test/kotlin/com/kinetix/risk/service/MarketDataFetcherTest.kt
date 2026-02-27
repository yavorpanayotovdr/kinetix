package com.kinetix.risk.service

import com.kinetix.common.model.*
import com.kinetix.risk.client.PriceServiceClient
import com.kinetix.risk.client.RatesServiceClient
import com.kinetix.risk.model.DiscoveredDependency
import com.kinetix.risk.model.FetchFailure
import com.kinetix.risk.model.FetchSuccess
import com.kinetix.risk.model.ScalarMarketData
import com.kinetix.risk.model.TimeSeriesMarketData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
        val success = result[0].shouldBeInstanceOf<FetchSuccess>()
        val scalar = success.value.shouldBeInstanceOf<ScalarMarketData>()
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
        val success = result[0].shouldBeInstanceOf<FetchSuccess>()
        val ts = success.value.shouldBeInstanceOf<TimeSeriesMarketData>()
        ts.dataType shouldBe "HISTORICAL_PRICES"
        ts.points shouldHaveSize 2
    }

    test("returns failures for unfetchable data types like volatility surface when client is null") {
        val deps = listOf(
            DiscoveredDependency("SPOT_PRICE", "AAPL", "EQUITY"),
            DiscoveredDependency("VOLATILITY_SURFACE", "AAPL", "EQUITY"),
            DiscoveredDependency("YIELD_CURVE", "AAPL", "EQUITY"),
        )

        coEvery { priceServiceClient.getLatestPrice(InstrumentId("AAPL")) } returns pricePoint()

        val result = fetcher.fetch(deps)

        result shouldHaveSize 3
        result[0].shouldBeInstanceOf<FetchSuccess>()

        val volFailure = result[1].shouldBeInstanceOf<FetchFailure>()
        volFailure.reason shouldBe "CLIENT_UNAVAILABLE"
        volFailure.service shouldBe "volatility-service"

        val yieldFailure = result[2].shouldBeInstanceOf<FetchFailure>()
        yieldFailure.reason shouldBe "CLIENT_UNAVAILABLE"
        yieldFailure.service shouldBe "rates-service"
    }

    test("returns a failure and a success when one dependency throws an exception") {
        val deps = listOf(
            DiscoveredDependency("SPOT_PRICE", "FAIL", "EQUITY"),
            DiscoveredDependency("SPOT_PRICE", "AAPL", "EQUITY"),
        )

        coEvery { priceServiceClient.getLatestPrice(InstrumentId("FAIL")) } throws RuntimeException("price unavailable")
        coEvery { priceServiceClient.getLatestPrice(InstrumentId("AAPL")) } returns pricePoint()

        val result = fetcher.fetch(deps)

        result shouldHaveSize 2
        val failure = result[0].shouldBeInstanceOf<FetchFailure>()
        failure.reason shouldBe "EXCEPTION"
        failure.errorMessage shouldBe "price unavailable"
        failure.service shouldBe "price-service"

        val success = result[1].shouldBeInstanceOf<FetchSuccess>()
        success.value.instrumentId shouldBe "AAPL"
    }

    test("returns empty list when given no dependencies") {
        val result = fetcher.fetch(emptyList())

        result.shouldBeEmpty()
    }

    test("returns FetchFailure with NOT_FOUND reason when client returns null") {
        val deps = listOf(
            DiscoveredDependency("SPOT_PRICE", "UNKNOWN", "EQUITY"),
        )

        coEvery { priceServiceClient.getLatestPrice(InstrumentId("UNKNOWN")) } returns null

        val result = fetcher.fetch(deps)

        result shouldHaveSize 1
        val failure = result[0].shouldBeInstanceOf<FetchFailure>()
        failure.reason shouldBe "NOT_FOUND"
        failure.service shouldBe "price-service"
        failure.dependency.instrumentId shouldBe "UNKNOWN"
    }

    test("returns FetchFailure with CLIENT_UNAVAILABLE reason when client is null") {
        val fetcher = MarketDataFetcher(priceServiceClient)
        val deps = listOf(
            DiscoveredDependency("YIELD_CURVE", "USD_SOFR", "RATES"),
        )

        val result = fetcher.fetch(deps)

        result shouldHaveSize 1
        val failure = result[0].shouldBeInstanceOf<FetchFailure>()
        failure.reason shouldBe "CLIENT_UNAVAILABLE"
        failure.service shouldBe "rates-service"
    }

    test("resolves correct URL for each data type") {
        val fetcherWithUrls = MarketDataFetcher(
            priceServiceClient,
            priceServiceBaseUrl = "http://price:8082",
            ratesServiceBaseUrl = "http://rates:8088",
            referenceDataServiceBaseUrl = "http://refdata:8089",
            volatilityServiceBaseUrl = "http://vol:8090",
            correlationServiceBaseUrl = "http://corr:8091",
        )

        coEvery { priceServiceClient.getLatestPrice(any()) } returns null

        val deps = listOf(
            DiscoveredDependency("SPOT_PRICE", "AAPL", "EQUITY"),
        )

        val result = fetcherWithUrls.fetch(deps)

        result shouldHaveSize 1
        val failure = result[0].shouldBeInstanceOf<FetchFailure>()
        failure.url shouldBe "http://price:8082/api/prices/AAPL/latest"
    }

    test("captures durationMs for each fetch result") {
        val deps = listOf(
            DiscoveredDependency("SPOT_PRICE", "AAPL", "EQUITY"),
        )

        coEvery { priceServiceClient.getLatestPrice(InstrumentId("AAPL")) } returns null

        val result = fetcher.fetch(deps)

        result shouldHaveSize 1
        val failure = result[0].shouldBeInstanceOf<FetchFailure>()
        failure.durationMs shouldBeGreaterThanOrEqual 0L
        failure.timestamp.shouldNotBeNull()
    }

    test("returns FetchFailure with EXCEPTION reason and extracts error message when client throws") {
        val deps = listOf(
            DiscoveredDependency("SPOT_PRICE", "ERR", "EQUITY"),
        )

        coEvery { priceServiceClient.getLatestPrice(InstrumentId("ERR")) } throws IllegalStateException("connection refused")

        val result = fetcher.fetch(deps)

        result shouldHaveSize 1
        val failure = result[0].shouldBeInstanceOf<FetchFailure>()
        failure.reason shouldBe "EXCEPTION"
        failure.errorMessage shouldBe "connection refused"
        failure.httpStatus shouldBe null
    }

    test("resolves service name correctly for all data types") {
        val ratesClient = mockk<RatesServiceClient>()
        val fetcherWithRates = MarketDataFetcher(priceServiceClient, ratesServiceClient = ratesClient)

        coEvery { priceServiceClient.getLatestPrice(any()) } returns null
        coEvery { ratesClient.getLatestYieldCurve(any()) } returns null

        val deps = listOf(
            DiscoveredDependency("SPOT_PRICE", "AAPL", "EQUITY"),
            DiscoveredDependency("YIELD_CURVE", "USD", "RATES"),
        )

        val result = fetcherWithRates.fetch(deps)

        result shouldHaveSize 2
        val priceFailure = result[0].shouldBeInstanceOf<FetchFailure>()
        priceFailure.service shouldBe "price-service"

        val ratesFailure = result[1].shouldBeInstanceOf<FetchFailure>()
        ratesFailure.service shouldBe "rates-service"
    }
})
