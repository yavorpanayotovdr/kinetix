package com.kinetix.risk.service

import com.kinetix.common.model.*
import com.kinetix.proto.risk.DataDependenciesResponse
import com.kinetix.proto.risk.MarketDataDependency
import com.kinetix.proto.risk.MarketDataType
import com.kinetix.risk.client.RiskEngineClient
import com.kinetix.risk.model.DiscoveredDependency
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.math.BigDecimal
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

class DependenciesDiscovererTest : FunSpec({

    val riskEngineClient = mockk<RiskEngineClient>()
    val discoverer = DependenciesDiscoverer(riskEngineClient)

    beforeEach {
        clearMocks(riskEngineClient)
    }

    test("discovers dependencies from risk engine and returns domain objects") {
        val positions = listOf(position())
        val depsResponse = DataDependenciesResponse.newBuilder()
            .addDependencies(dependency(MarketDataType.SPOT_PRICE, parameters = mapOf("key" to "val")))
            .addDependencies(dependency(MarketDataType.HISTORICAL_PRICES, parameters = mapOf("lookbackDays" to "252")))
            .build()

        coEvery { riskEngineClient.discoverDependencies(positions, "PARAMETRIC", "CL_95") } returns depsResponse

        val result = discoverer.discover(positions, "PARAMETRIC", "CL_95")

        result shouldHaveSize 2
        result shouldContainExactlyInAnyOrder listOf(
            DiscoveredDependency("SPOT_PRICE", "AAPL", "EQUITY", mapOf("key" to "val")),
            DiscoveredDependency("HISTORICAL_PRICES", "AAPL", "EQUITY", mapOf("lookbackDays" to "252")),
        )
    }

    test("returns empty list when no dependencies discovered") {
        val positions = listOf(position())
        val depsResponse = DataDependenciesResponse.newBuilder().build()

        coEvery { riskEngineClient.discoverDependencies(positions, "PARAMETRIC", "CL_95") } returns depsResponse

        val result = discoverer.discover(positions, "PARAMETRIC", "CL_95")

        result.shouldBeEmpty()
    }

    test("returns empty list when discovery fails") {
        val positions = listOf(position())
        coEvery { riskEngineClient.discoverDependencies(any(), any(), any()) } throws RuntimeException("gRPC unavailable")

        val result = discoverer.discover(positions, "PARAMETRIC", "CL_95")

        result.shouldBeEmpty()
    }

    test("deduplicates by data type and instrument id") {
        val positions = listOf(position())
        val depsResponse = DataDependenciesResponse.newBuilder()
            .addDependencies(dependency(MarketDataType.SPOT_PRICE))
            .addDependencies(dependency(MarketDataType.SPOT_PRICE))
            .addDependencies(dependency(MarketDataType.SPOT_PRICE, instrumentId = "MSFT"))
            .build()

        coEvery { riskEngineClient.discoverDependencies(positions, "PARAMETRIC", "CL_95") } returns depsResponse

        val result = discoverer.discover(positions, "PARAMETRIC", "CL_95")

        result shouldHaveSize 2
        result.map { it.instrumentId } shouldContainExactlyInAnyOrder listOf("AAPL", "MSFT")
    }
})
