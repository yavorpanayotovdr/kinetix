package com.kinetix.risk.mapper

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ComponentBreakdown
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.GreekValues
import com.kinetix.risk.model.GreeksResult
import com.kinetix.risk.model.PositionRisk
import com.kinetix.risk.model.RunStatus
import com.kinetix.risk.model.TriggerType
import com.kinetix.risk.model.ValuationJob
import com.kinetix.risk.model.ValuationOutput
import com.kinetix.risk.model.ValuationResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val BASE_TIME = Instant.parse("2025-06-15T10:00:00Z")
private val COMPLETED_TIME = Instant.parse("2025-06-15T10:00:00.200Z")
private val VALUATION_DATE = LocalDate.of(2025, 6, 15)

private fun completedJob(
    jobId: UUID = UUID.randomUUID(),
    portfolioId: String = "port-1",
) = ValuationJob(
    jobId = jobId,
    portfolioId = portfolioId,
    triggerType = TriggerType.ON_DEMAND,
    status = RunStatus.COMPLETED,
    startedAt = BASE_TIME,
    valuationDate = VALUATION_DATE,
    completedAt = COMPLETED_TIME,
    durationMs = 200,
    calculationType = "PARAMETRIC",
    confidenceLevel = "CL_95",
    varValue = 5000.0,
    expectedShortfall = 6250.0,
    pvValue = 1_800_000.0,
    delta = 0.85,
    gamma = 0.02,
    vega = 50.0,
    theta = -15.0,
    rho = 0.03,
    positionRiskSnapshot = listOf(
        PositionRisk(
            instrumentId = InstrumentId("AAPL"),
            assetClass = AssetClass.EQUITY,
            marketValue = BigDecimal("17000.00"),
            delta = 0.85,
            gamma = 0.02,
            vega = null,
            varContribution = BigDecimal("3000.00"),
            esContribution = BigDecimal("3750.00"),
            percentageOfTotal = BigDecimal("60.00"),
        ),
    ),
    componentBreakdownSnapshot = listOf(
        ComponentBreakdown(AssetClass.EQUITY, 5000.0, 100.0),
    ),
    computedOutputsSnapshot = setOf(ValuationOutput.VAR, ValuationOutput.EXPECTED_SHORTFALL, ValuationOutput.GREEKS),
    assetClassGreeksSnapshot = listOf(
        GreekValues(AssetClass.EQUITY, 0.85, 0.02, 50.0),
    ),
)

private fun valuationResult(
    jobId: UUID? = UUID.randomUUID(),
    portfolioId: String = "port-1",
    valuationDate: LocalDate? = VALUATION_DATE,
) = ValuationResult(
    portfolioId = PortfolioId(portfolioId),
    calculationType = CalculationType.HISTORICAL,
    confidenceLevel = ConfidenceLevel.CL_99,
    varValue = 4000.0,
    expectedShortfall = 5000.0,
    componentBreakdown = listOf(ComponentBreakdown(AssetClass.EQUITY, 4000.0, 100.0)),
    greeks = GreeksResult(
        assetClassGreeks = listOf(GreekValues(AssetClass.EQUITY, 0.7, 0.015, 30.0)),
        theta = -12.0,
        rho = 0.02,
    ),
    calculatedAt = COMPLETED_TIME,
    computedOutputs = setOf(ValuationOutput.VAR, ValuationOutput.GREEKS),
    pvValue = 1_500_000.0,
    positionRisk = emptyList(),
    jobId = jobId,
    modelVersion = "v2.1",
    valuationDate = valuationDate,
)

class RunSnapshotMapperTest : FunSpec({

    test("maps completed ValuationJob to RunSnapshot with all fields") {
        val job = completedJob()
        val snapshot = job.toRunSnapshot("Base")

        snapshot.jobId shouldBe job.jobId
        snapshot.label shouldBe "Base"
        snapshot.valuationDate shouldBe VALUATION_DATE
        snapshot.calculationType shouldBe CalculationType.PARAMETRIC
        snapshot.confidenceLevel shouldBe ConfidenceLevel.CL_95
        snapshot.varValue shouldBe 5000.0
        snapshot.expectedShortfall shouldBe 6250.0
        snapshot.pvValue shouldBe 1_800_000.0
        snapshot.delta shouldBe 0.85
        snapshot.gamma shouldBe 0.02
        snapshot.vega shouldBe 50.0
        snapshot.theta shouldBe -15.0
        snapshot.rho shouldBe 0.03
        snapshot.positionRisks shouldHaveSize 1
        snapshot.positionRisks[0].instrumentId shouldBe InstrumentId("AAPL")
        snapshot.componentBreakdowns shouldHaveSize 1
        snapshot.componentBreakdowns[0].assetClass shouldBe AssetClass.EQUITY
        snapshot.calculatedAt shouldBe COMPLETED_TIME
    }

    test("maps ValuationResult to RunSnapshot") {
        val result = valuationResult()
        val snapshot = result.toRunSnapshot("Target", VALUATION_DATE)

        snapshot.jobId shouldBe result.jobId
        snapshot.label shouldBe "Target"
        snapshot.valuationDate shouldBe VALUATION_DATE
        snapshot.calculationType shouldBe CalculationType.HISTORICAL
        snapshot.confidenceLevel shouldBe ConfidenceLevel.CL_99
        snapshot.varValue shouldBe 4000.0
        snapshot.expectedShortfall shouldBe 5000.0
        snapshot.pvValue shouldBe 1_500_000.0
        snapshot.theta shouldBe -12.0
        snapshot.rho shouldBe 0.02
        snapshot.modelVersion shouldBe "v2.1"
        snapshot.componentBreakdowns shouldHaveSize 1
        snapshot.calculatedAt shouldBe COMPLETED_TIME
    }

    test("uses label parameter") {
        val job = completedJob()
        val snapshot = job.toRunSnapshot("My Label")

        snapshot.label shouldBe "My Label"
    }

    test("builds parameters map from calculationType and confidenceLevel") {
        val job = completedJob()
        val snapshot = job.toRunSnapshot("Base")

        snapshot.parameters["calculationType"] shouldBe "PARAMETRIC"
        snapshot.parameters["confidenceLevel"] shouldBe "CL_95"
    }

    test("uses startedAt for calculatedAt when completedAt is null") {
        val job = completedJob().copy(completedAt = null)
        val snapshot = job.toRunSnapshot("Base")

        snapshot.calculatedAt shouldBe BASE_TIME
    }

    test("ValuationResult uses valuationDate from result when present") {
        val result = valuationResult(valuationDate = VALUATION_DATE)
        val snapshot = result.toRunSnapshot("Target", LocalDate.of(2025, 1, 1))

        snapshot.valuationDate shouldBe VALUATION_DATE
    }

    test("ValuationResult falls back to provided valuationDate when result has none") {
        val result = valuationResult(valuationDate = null)
        val fallback = LocalDate.of(2025, 1, 1)
        val snapshot = result.toRunSnapshot("Target", fallback)

        snapshot.valuationDate shouldBe fallback
    }

    test("ValuationResult sums greeks delta and gamma from asset class breakdown") {
        val result = valuationResult()
        val snapshot = result.toRunSnapshot("Target", VALUATION_DATE)

        snapshot.delta.shouldNotBeNull()
        snapshot.delta shouldBe 0.7
        snapshot.gamma shouldBe 0.015
        snapshot.vega shouldBe 30.0
    }
})
