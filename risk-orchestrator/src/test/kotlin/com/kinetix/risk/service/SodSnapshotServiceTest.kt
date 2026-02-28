package com.kinetix.risk.service

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.Money
import com.kinetix.common.model.PortfolioId
import com.kinetix.common.model.Position
import com.kinetix.risk.cache.LatestVaRCache
import com.kinetix.risk.client.PositionProvider
import com.kinetix.risk.model.*
import com.kinetix.risk.persistence.DailyRiskSnapshotRepository
import com.kinetix.risk.persistence.SodBaselineRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

private val PORTFOLIO = PortfolioId("port-1")
private val TODAY = LocalDate.of(2025, 1, 15)

private fun position(
    instrumentId: String = "AAPL",
    assetClass: AssetClass = AssetClass.EQUITY,
    quantity: String = "100",
    price: String = "150.00",
) = Position(
    portfolioId = PORTFOLIO,
    instrumentId = InstrumentId(instrumentId),
    assetClass = assetClass,
    quantity = BigDecimal(quantity),
    averageCost = Money(BigDecimal(price), Currency.getInstance("USD")),
    marketPrice = Money(BigDecimal(price), Currency.getInstance("USD")),
)

private fun valuationResult(
    portfolioId: PortfolioId = PORTFOLIO,
    positionRisk: List<PositionRisk> = listOf(
        PositionRisk(
            instrumentId = InstrumentId("AAPL"),
            assetClass = AssetClass.EQUITY,
            marketValue = BigDecimal("15000.00"),
            delta = 0.85,
            gamma = 0.02,
            vega = 1500.0,
            varContribution = BigDecimal("500.00"),
            esContribution = BigDecimal("600.00"),
            percentageOfTotal = BigDecimal("100.00"),
        ),
    ),
) = ValuationResult(
    portfolioId = portfolioId,
    calculationType = CalculationType.PARAMETRIC,
    confidenceLevel = ConfidenceLevel.CL_95,
    varValue = 500.0,
    expectedShortfall = 600.0,
    componentBreakdown = emptyList(),
    greeks = GreeksResult(
        assetClassGreeks = listOf(
            GreekValues(assetClass = AssetClass.EQUITY, delta = 0.85, gamma = 0.02, vega = 1500.0),
        ),
        theta = -50.0,
        rho = 30.0,
    ),
    calculatedAt = Instant.parse("2025-01-15T08:00:00Z"),
    computedOutputs = setOf(ValuationOutput.VAR, ValuationOutput.GREEKS),
    positionRisk = positionRisk,
)

class SodSnapshotServiceTest : FunSpec({

    val sodBaselineRepository = mockk<SodBaselineRepository>()
    val dailyRiskSnapshotRepository = mockk<DailyRiskSnapshotRepository>()
    val varCache = mockk<LatestVaRCache>()
    val varCalculationService = mockk<VaRCalculationService>()
    val positionProvider = mockk<PositionProvider>()

    val service = SodSnapshotService(
        sodBaselineRepository = sodBaselineRepository,
        dailyRiskSnapshotRepository = dailyRiskSnapshotRepository,
        varCache = varCache,
        varCalculationService = varCalculationService,
        positionProvider = positionProvider,
    )

    beforeEach {
        clearMocks(sodBaselineRepository, dailyRiskSnapshotRepository, varCache, varCalculationService, positionProvider)
    }

    test("creates snapshot from provided ValuationResult and stores baseline metadata") {
        val result = valuationResult()
        coEvery { dailyRiskSnapshotRepository.saveAll(any()) } just Runs
        coEvery { sodBaselineRepository.save(any()) } just Runs

        service.createSnapshot(PORTFOLIO, SnapshotType.MANUAL, result, TODAY)

        coVerify {
            dailyRiskSnapshotRepository.saveAll(withArg { snapshots ->
                snapshots.size shouldBe 1
                snapshots[0].portfolioId shouldBe PORTFOLIO
                snapshots[0].snapshotDate shouldBe TODAY
                snapshots[0].instrumentId shouldBe InstrumentId("AAPL")
                snapshots[0].delta shouldBe 0.85
                snapshots[0].gamma shouldBe 0.02
                snapshots[0].vega shouldBe 1500.0
            })
        }
        coVerify {
            sodBaselineRepository.save(withArg { baseline ->
                baseline.portfolioId shouldBe PORTFOLIO
                baseline.baselineDate shouldBe TODAY
                baseline.snapshotType shouldBe SnapshotType.MANUAL
            })
        }
    }

    test("creates snapshot using cached VaR result when no ValuationResult provided") {
        val result = valuationResult()
        coEvery { varCache.get(PORTFOLIO.value) } returns result
        coEvery { dailyRiskSnapshotRepository.saveAll(any()) } just Runs
        coEvery { sodBaselineRepository.save(any()) } just Runs

        service.createSnapshot(PORTFOLIO, SnapshotType.MANUAL, date = TODAY)

        coVerify { varCache.get(PORTFOLIO.value) }
        coVerify { dailyRiskSnapshotRepository.saveAll(any()) }
        coVerify { sodBaselineRepository.save(any()) }
    }

    test("triggers VaR calculation when no cached result and no ValuationResult provided") {
        val result = valuationResult()
        coEvery { varCache.get(PORTFOLIO.value) } returns null
        coEvery { positionProvider.getPositions(PORTFOLIO) } returns listOf(position())
        coEvery { varCalculationService.calculateVaR(any(), any()) } returns result
        coEvery { dailyRiskSnapshotRepository.saveAll(any()) } just Runs
        coEvery { sodBaselineRepository.save(any()) } just Runs

        service.createSnapshot(PORTFOLIO, SnapshotType.AUTO, date = TODAY)

        coVerify { varCalculationService.calculateVaR(any(), any()) }
        coVerify { dailyRiskSnapshotRepository.saveAll(any()) }
    }

    test("replaces existing baseline when creating new snapshot for same date") {
        val result = valuationResult()
        coEvery { dailyRiskSnapshotRepository.saveAll(any()) } just Runs
        coEvery { sodBaselineRepository.save(any()) } just Runs

        service.createSnapshot(PORTFOLIO, SnapshotType.MANUAL, result, TODAY)
        service.createSnapshot(PORTFOLIO, SnapshotType.MANUAL, result, TODAY)

        coVerify(exactly = 2) { sodBaselineRepository.save(any()) }
    }

    test("getBaselineStatus returns status with exists=true when baseline exists") {
        val baseline = SodBaseline(
            id = 1,
            portfolioId = PORTFOLIO,
            baselineDate = TODAY,
            snapshotType = SnapshotType.MANUAL,
            createdAt = Instant.parse("2025-01-15T08:00:00Z"),
        )
        coEvery { sodBaselineRepository.findByPortfolioIdAndDate(PORTFOLIO, TODAY) } returns baseline

        val status = service.getBaselineStatus(PORTFOLIO, TODAY)

        status.exists shouldBe true
        status.snapshotType shouldBe SnapshotType.MANUAL
        status.createdAt shouldBe Instant.parse("2025-01-15T08:00:00Z")
        status.baselineDate shouldBe "2025-01-15"
    }

    test("getBaselineStatus returns status with exists=false when no baseline") {
        coEvery { sodBaselineRepository.findByPortfolioIdAndDate(PORTFOLIO, TODAY) } returns null

        val status = service.getBaselineStatus(PORTFOLIO, TODAY)

        status.exists shouldBe false
        status.snapshotType shouldBe null
        status.createdAt shouldBe null
        status.baselineDate shouldBe null
    }

    test("resetBaseline deletes baseline and snapshot rows") {
        coEvery { sodBaselineRepository.deleteByPortfolioIdAndDate(PORTFOLIO, TODAY) } just Runs

        service.resetBaseline(PORTFOLIO, TODAY)

        coVerify { sodBaselineRepository.deleteByPortfolioIdAndDate(PORTFOLIO, TODAY) }
    }

    test("creates snapshot with multiple position risks from ValuationResult") {
        val result = valuationResult(
            positionRisk = listOf(
                PositionRisk(
                    instrumentId = InstrumentId("AAPL"),
                    assetClass = AssetClass.EQUITY,
                    marketValue = BigDecimal("15000.00"),
                    delta = 0.85, gamma = 0.02, vega = 1500.0,
                    varContribution = BigDecimal("300.00"),
                    esContribution = BigDecimal("400.00"),
                    percentageOfTotal = BigDecimal("60.00"),
                ),
                PositionRisk(
                    instrumentId = InstrumentId("MSFT"),
                    assetClass = AssetClass.EQUITY,
                    marketValue = BigDecimal("30000.00"),
                    delta = 0.90, gamma = 0.03, vega = 2000.0,
                    varContribution = BigDecimal("200.00"),
                    esContribution = BigDecimal("250.00"),
                    percentageOfTotal = BigDecimal("40.00"),
                ),
            ),
        )
        coEvery { dailyRiskSnapshotRepository.saveAll(any()) } just Runs
        coEvery { sodBaselineRepository.save(any()) } just Runs

        service.createSnapshot(PORTFOLIO, SnapshotType.MANUAL, result, TODAY)

        coVerify {
            dailyRiskSnapshotRepository.saveAll(withArg { snapshots ->
                snapshots.size shouldBe 2
                snapshots[0].instrumentId shouldBe InstrumentId("AAPL")
                snapshots[1].instrumentId shouldBe InstrumentId("MSFT")
            })
        }
    }
})
