package com.kinetix.risk.service

import com.kinetix.common.model.*
import com.kinetix.proto.risk.DataDependenciesResponse
import com.kinetix.proto.risk.MarketDataDependency
import com.kinetix.proto.risk.MarketDataType
import com.kinetix.risk.client.PriceServiceClient
import com.kinetix.risk.client.RiskEngineClient
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

private fun position(
    instrumentId: String = "AAPL",
    assetClass: AssetClass = AssetClass.EQUITY,
) = Position(
    portfolioId = PortfolioId("port-1"),
    instrumentId = InstrumentId(instrumentId),
    assetClass = assetClass,
    quantity = BigDecimal("100"),
    averageCost = Money(BigDecimal("150.00"), USD),
    marketPrice = Money(BigDecimal("170.00"), USD),
)

private fun dependency(
    dataType: MarketDataType,
    instrumentId: String = "AAPL",
    assetClass: String = "EQUITY",
    parameters: Map<String, String> = emptyMap(),
) = MarketDataDependency.newBuilder()
    .setDataType(dataType)
    .setInstrumentId(instrumentId)
    .setAssetClass(assetClass)
    .putAllParameters(parameters)
    .build()

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

    val riskEngineClient = mockk<RiskEngineClient>()
    val priceServiceClient = mockk<PriceServiceClient>()
    val fetcher = MarketDataFetcher(riskEngineClient, priceServiceClient)

    beforeEach {
        clearMocks(riskEngineClient, priceServiceClient)
    }

    test("fetches spot price from price service") {
        val positions = listOf(position())
        val depsResponse = DataDependenciesResponse.newBuilder()
            .addDependencies(dependency(MarketDataType.SPOT_PRICE))
            .build()

        coEvery { riskEngineClient.discoverDependencies(positions, "PARAMETRIC", "CL_95") } returns depsResponse
        coEvery { priceServiceClient.getLatestPrice(InstrumentId("AAPL")) } returns pricePoint()

        val result = fetcher.fetch(positions, "PARAMETRIC", "CL_95")

        result shouldHaveSize 1
        val scalar = result[0].shouldBeInstanceOf<ScalarMarketData>()
        scalar.dataType shouldBe "SPOT_PRICE"
        scalar.instrumentId shouldBe "AAPL"
        scalar.value shouldBe 170.50
    }

    test("fetches historical prices from price service") {
        val positions = listOf(position())
        val depsResponse = DataDependenciesResponse.newBuilder()
            .addDependencies(
                dependency(
                    MarketDataType.HISTORICAL_PRICES,
                    parameters = mapOf("lookbackDays" to "252"),
                )
            )
            .build()

        coEvery { riskEngineClient.discoverDependencies(positions, "PARAMETRIC", "CL_95") } returns depsResponse
        coEvery { priceServiceClient.getPriceHistory(InstrumentId("AAPL"), any(), any()) } returns listOf(
            pricePoint(amount = "168.00", timestamp = Instant.parse("2026-02-22T10:00:00Z")),
            pricePoint(amount = "170.50", timestamp = Instant.parse("2026-02-23T10:00:00Z")),
        )

        val result = fetcher.fetch(positions, "PARAMETRIC", "CL_95")

        result shouldHaveSize 1
        val ts = result[0].shouldBeInstanceOf<TimeSeriesMarketData>()
        ts.dataType shouldBe "HISTORICAL_PRICES"
        ts.points shouldHaveSize 2
    }

    test("returns empty list when no dependencies discovered") {
        val positions = listOf(position())
        val depsResponse = DataDependenciesResponse.newBuilder().build()

        coEvery { riskEngineClient.discoverDependencies(positions, "PARAMETRIC", "CL_95") } returns depsResponse

        val result = fetcher.fetch(positions, "PARAMETRIC", "CL_95")

        result.shouldBeEmpty()
        coVerify(exactly = 0) { priceServiceClient.getLatestPrice(any()) }
    }

    test("returns empty list when dependency discovery fails") {
        val positions = listOf(position())
        coEvery { riskEngineClient.discoverDependencies(any(), any(), any()) } throws RuntimeException("gRPC unavailable")

        val result = fetcher.fetch(positions, "PARAMETRIC", "CL_95")

        result.shouldBeEmpty()
    }

    test("deduplicates dependencies by data type and instrument id") {
        val positions = listOf(position())
        val depsResponse = DataDependenciesResponse.newBuilder()
            .addDependencies(dependency(MarketDataType.SPOT_PRICE))
            .addDependencies(dependency(MarketDataType.SPOT_PRICE))
            .build()

        coEvery { riskEngineClient.discoverDependencies(positions, "PARAMETRIC", "CL_95") } returns depsResponse
        coEvery { priceServiceClient.getLatestPrice(InstrumentId("AAPL")) } returns pricePoint()

        val result = fetcher.fetch(positions, "PARAMETRIC", "CL_95")

        result shouldHaveSize 1
        coVerify(exactly = 1) { priceServiceClient.getLatestPrice(InstrumentId("AAPL")) }
    }

    test("skips unfetchable data types like volatility surface") {
        val positions = listOf(position())
        val depsResponse = DataDependenciesResponse.newBuilder()
            .addDependencies(dependency(MarketDataType.SPOT_PRICE))
            .addDependencies(dependency(MarketDataType.VOLATILITY_SURFACE))
            .addDependencies(dependency(MarketDataType.YIELD_CURVE))
            .build()

        coEvery { riskEngineClient.discoverDependencies(positions, "PARAMETRIC", "CL_95") } returns depsResponse
        coEvery { priceServiceClient.getLatestPrice(InstrumentId("AAPL")) } returns pricePoint()

        val result = fetcher.fetch(positions, "PARAMETRIC", "CL_95")

        result shouldHaveSize 1
        result[0].shouldBeInstanceOf<ScalarMarketData>()
    }
})
