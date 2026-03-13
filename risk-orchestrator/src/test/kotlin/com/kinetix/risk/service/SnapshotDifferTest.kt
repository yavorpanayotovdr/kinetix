package com.kinetix.risk.service

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ComponentBreakdown
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.PositionRisk
import com.kinetix.risk.model.PositionChangeType
import com.kinetix.risk.model.RunSnapshot
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private fun positionRisk(
    instrumentId: String,
    assetClass: AssetClass = AssetClass.EQUITY,
    marketValue: String = "10000.00",
    varContribution: String = "500.00",
    esContribution: String = "625.00",
    percentageOfTotal: String = "10.00",
    delta: Double? = 0.5,
    gamma: Double? = 0.01,
    vega: Double? = null,
) = PositionRisk(
    instrumentId = InstrumentId(instrumentId),
    assetClass = assetClass,
    marketValue = BigDecimal(marketValue),
    delta = delta,
    gamma = gamma,
    vega = vega,
    varContribution = BigDecimal(varContribution),
    esContribution = BigDecimal(esContribution),
    percentageOfTotal = BigDecimal(percentageOfTotal),
)

private fun snapshot(
    varValue: Double? = 5000.0,
    es: Double? = 6250.0,
    pvValue: Double? = 100000.0,
    delta: Double? = 0.5,
    gamma: Double? = 0.01,
    vega: Double? = 100.0,
    theta: Double? = -50.0,
    rho: Double? = 25.0,
    positionRisks: List<PositionRisk> = emptyList(),
    componentBreakdowns: List<ComponentBreakdown> = emptyList(),
    calculationType: CalculationType? = CalculationType.PARAMETRIC,
    confidenceLevel: ConfidenceLevel? = ConfidenceLevel.CL_95,
    label: String = "Test",
) = RunSnapshot(
    jobId = UUID.randomUUID(),
    label = label,
    valuationDate = LocalDate.of(2025, 1, 15),
    calculationType = calculationType,
    confidenceLevel = confidenceLevel,
    varValue = varValue,
    expectedShortfall = es,
    pvValue = pvValue,
    delta = delta,
    gamma = gamma,
    vega = vega,
    theta = theta,
    rho = rho,
    positionRisks = positionRisks,
    componentBreakdowns = componentBreakdowns,
    modelVersion = null,
    parameters = buildMap {
        calculationType?.let { put("calculationType", it.name) }
        confidenceLevel?.let { put("confidenceLevel", it.name) }
    },
    calculatedAt = Instant.now(),
)

class SnapshotDifferTest : FunSpec({

    val differ = SnapshotDiffer()

    context("computePortfolioDiff") {

        test("computes positive VaR change when target exceeds base") {
            val base = snapshot(varValue = 5000.0)
            val target = snapshot(varValue = 7000.0)

            val diff = differ.computePortfolioDiff(base, target)

            diff.varChange shouldBeExactly 2000.0
            diff.varChangePercent shouldBe 40.0
        }

        test("computes negative VaR change when target is lower") {
            val base = snapshot(varValue = 5000.0)
            val target = snapshot(varValue = 3000.0)

            val diff = differ.computePortfolioDiff(base, target)

            diff.varChange shouldBeExactly -2000.0
            diff.varChangePercent shouldBe -40.0
        }

        test("computes zero percent change when base VaR is zero") {
            val base = snapshot(varValue = 0.0)
            val target = snapshot(varValue = 1000.0)

            val diff = differ.computePortfolioDiff(base, target)

            diff.varChange shouldBeExactly 1000.0
            diff.varChangePercent.shouldBeNull()
        }
    }

    context("matchPositions") {

        test("classifies position as NEW when only in target") {
            val target = listOf(positionRisk("AAPL"))

            val diffs = differ.matchPositions(emptyList(), target)

            diffs shouldHaveSize 1
            diffs[0].instrumentId shouldBe InstrumentId("AAPL")
            diffs[0].changeType shouldBe PositionChangeType.NEW
        }

        test("classifies position as REMOVED when only in base") {
            val base = listOf(positionRisk("AAPL"))

            val diffs = differ.matchPositions(base, emptyList())

            diffs shouldHaveSize 1
            diffs[0].instrumentId shouldBe InstrumentId("AAPL")
            diffs[0].changeType shouldBe PositionChangeType.REMOVED
        }

        test("classifies position as MODIFIED when market value differs") {
            val base = listOf(positionRisk("AAPL", marketValue = "10000.00"))
            val target = listOf(positionRisk("AAPL", marketValue = "12000.00"))

            val diffs = differ.matchPositions(base, target)

            diffs shouldHaveSize 1
            diffs[0].changeType shouldBe PositionChangeType.MODIFIED
            diffs[0].baseMarketValue shouldBe BigDecimal("10000.00")
            diffs[0].targetMarketValue shouldBe BigDecimal("12000.00")
            diffs[0].marketValueChange shouldBe BigDecimal("12000.00") - BigDecimal("10000.00")
        }

        test("classifies position as UNCHANGED when all values match") {
            val position = positionRisk("AAPL")
            val diffs = differ.matchPositions(listOf(position), listOf(position))

            diffs shouldHaveSize 1
            diffs[0].changeType shouldBe PositionChangeType.UNCHANGED
        }

        test("handles empty position lists") {
            val diffs = differ.matchPositions(emptyList(), emptyList())

            diffs.shouldBeEmpty()
        }
    }

    context("matchComponents") {

        test("computes component diff by outer-joining on asset class") {
            val baseComponents = listOf(
                ComponentBreakdown(AssetClass.EQUITY, 3000.0, 60.0),
            )
            val targetComponents = listOf(
                ComponentBreakdown(AssetClass.EQUITY, 4000.0, 50.0),
                ComponentBreakdown(AssetClass.FX, 1000.0, 12.5),
            )

            val diffs = differ.matchComponents(baseComponents, targetComponents)

            diffs shouldHaveSize 2

            val equityDiff = diffs.first { it.assetClass == AssetClass.EQUITY }
            equityDiff.baseContribution shouldBeExactly 3000.0
            equityDiff.targetContribution shouldBeExactly 4000.0
            equityDiff.change shouldBeExactly 1000.0

            val fxDiff = diffs.first { it.assetClass == AssetClass.FX }
            fxDiff.baseContribution shouldBeExactly 0.0
            fxDiff.targetContribution shouldBeExactly 1000.0
            fxDiff.change shouldBeExactly 1000.0
        }
    }

    context("diffParameters") {

        test("detects parameter differences between runs") {
            val base = snapshot(calculationType = CalculationType.PARAMETRIC, confidenceLevel = ConfidenceLevel.CL_95)
            val target = snapshot(calculationType = CalculationType.HISTORICAL, confidenceLevel = ConfidenceLevel.CL_95)

            val diffs = differ.diffParameters(base, target)

            diffs shouldHaveSize 1
            diffs[0].paramName shouldBe "calculationType"
            diffs[0].baseValue shouldBe "PARAMETRIC"
            diffs[0].targetValue shouldBe "HISTORICAL"
        }
    }
})
