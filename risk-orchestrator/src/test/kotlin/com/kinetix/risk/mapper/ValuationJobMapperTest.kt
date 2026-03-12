package com.kinetix.risk.mapper

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.risk.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val BASE_TIME = Instant.parse("2025-06-15T10:00:00Z")

private fun completedJobWithSnapshots(
    portfolioId: String = "port-1",
    positionRisk: List<PositionRisk> = listOf(
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
    componentBreakdown: List<ComponentBreakdown> = listOf(
        ComponentBreakdown(AssetClass.EQUITY, 5000.0, 100.0),
    ),
    assetClassGreeks: List<GreekValues> = listOf(
        GreekValues(AssetClass.EQUITY, 0.85, 0.02, 0.0),
    ),
    computedOutputs: Set<ValuationOutput> = setOf(ValuationOutput.VAR, ValuationOutput.EXPECTED_SHORTFALL, ValuationOutput.GREEKS),
) = ValuationJob(
    jobId = UUID.randomUUID(),
    portfolioId = portfolioId,
    triggerType = TriggerType.ON_DEMAND,
    status = RunStatus.COMPLETED,
    startedAt = BASE_TIME,
    valuationDate = LocalDate.of(2025, 6, 15),
    completedAt = BASE_TIME.plusMillis(200),
    durationMs = 200,
    calculationType = "PARAMETRIC",
    confidenceLevel = "CL_95",
    varValue = 5000.0,
    expectedShortfall = 6250.0,
    pvValue = 1_800_000.0,
    delta = 0.85,
    gamma = 0.02,
    vega = 0.0,
    theta = -15.0,
    rho = 0.03,
    positionRiskSnapshot = positionRisk,
    componentBreakdownSnapshot = componentBreakdown,
    computedOutputsSnapshot = computedOutputs,
    assetClassGreeksSnapshot = assetClassGreeks,
)

class ValuationJobMapperTest : FunSpec({

    test("reconstructs a full ValuationResult from a completed job with all snapshots") {
        val job = completedJobWithSnapshots()
        val result = job.toValuationResult()

        result.shouldNotBeNull()
        result.portfolioId.value shouldBe "port-1"
        result.calculationType shouldBe CalculationType.PARAMETRIC
        result.confidenceLevel shouldBe ConfidenceLevel.CL_95
        result.varValue shouldBe 5000.0
        result.expectedShortfall shouldBe 6250.0
        result.pvValue shouldBe 1_800_000.0
        result.calculatedAt shouldBe BASE_TIME.plusMillis(200)
        result.jobId shouldBe job.jobId

        result.positionRisk shouldHaveSize 1
        result.positionRisk[0].instrumentId shouldBe InstrumentId("AAPL")
        result.positionRisk[0].delta shouldBe 0.85
        result.positionRisk[0].vega shouldBe null

        result.componentBreakdown shouldHaveSize 1
        result.componentBreakdown[0].assetClass shouldBe AssetClass.EQUITY
        result.componentBreakdown[0].varContribution shouldBe 5000.0

        result.greeks.shouldNotBeNull()
        result.greeks!!.assetClassGreeks shouldHaveSize 1
        result.greeks!!.theta shouldBe -15.0
        result.greeks!!.rho shouldBe 0.03

        result.computedOutputs shouldBe setOf(
            ValuationOutput.VAR, ValuationOutput.EXPECTED_SHORTFALL, ValuationOutput.GREEKS,
        )
    }

    test("returns null for a RUNNING job") {
        val job = ValuationJob(
            jobId = UUID.randomUUID(),
            portfolioId = "port-1",
            triggerType = TriggerType.ON_DEMAND,
            status = RunStatus.RUNNING,
            startedAt = BASE_TIME,
            valuationDate = LocalDate.of(2025, 6, 15),
            calculationType = "PARAMETRIC",
            confidenceLevel = "CL_95",
        )
        job.toValuationResult().shouldBeNull()
    }

    test("returns null for a FAILED job") {
        val job = ValuationJob(
            jobId = UUID.randomUUID(),
            portfolioId = "port-1",
            triggerType = TriggerType.ON_DEMAND,
            status = RunStatus.FAILED,
            startedAt = BASE_TIME,
            valuationDate = LocalDate.of(2025, 6, 15),
            completedAt = BASE_TIME.plusMillis(50),
            durationMs = 50,
            calculationType = "PARAMETRIC",
            confidenceLevel = "CL_95",
            error = "Engine down",
        )
        job.toValuationResult().shouldBeNull()
    }

    test("returns null when calculationType is missing") {
        val job = completedJobWithSnapshots().copy(calculationType = null)
        job.toValuationResult().shouldBeNull()
    }

    test("returns null when confidenceLevel is missing") {
        val job = completedJobWithSnapshots().copy(confidenceLevel = null)
        job.toValuationResult().shouldBeNull()
    }

    test("handles empty snapshots gracefully") {
        val job = completedJobWithSnapshots(
            positionRisk = emptyList(),
            componentBreakdown = emptyList(),
            assetClassGreeks = emptyList(),
            computedOutputs = emptySet(),
        )
        val result = job.toValuationResult()

        result.shouldNotBeNull()
        result.positionRisk shouldHaveSize 0
        result.componentBreakdown shouldHaveSize 0
        result.greeks.shouldBeNull()
        result.computedOutputs shouldBe setOf(ValuationOutput.VAR, ValuationOutput.EXPECTED_SHORTFALL)
    }

    test("uses startedAt when completedAt is null") {
        val job = completedJobWithSnapshots().copy(completedAt = null)
        val result = job.toValuationResult()

        result.shouldNotBeNull()
        result.calculatedAt shouldBe BASE_TIME
    }

    test("toSummaryResponse includes valuationDate as ISO-8601 string") {
        val job = completedJobWithSnapshots()
        val response = job.toSummaryResponse()

        response.valuationDate shouldBe "2025-06-15"
    }

    test("toDetailResponse includes valuationDate as ISO-8601 string") {
        val job = completedJobWithSnapshots()
        val response = job.toDetailResponse()

        response.valuationDate shouldBe "2025-06-15"
    }

    test("toValuationResult includes valuationDate as LocalDate") {
        val job = completedJobWithSnapshots()
        val result = job.toValuationResult()

        result.shouldNotBeNull()
        result.valuationDate shouldBe LocalDate.of(2025, 6, 15)
    }
})
