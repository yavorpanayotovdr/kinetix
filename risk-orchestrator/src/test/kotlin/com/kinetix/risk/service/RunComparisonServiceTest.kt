package com.kinetix.risk.service

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ComparisonType
import com.kinetix.risk.model.ComponentBreakdown
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.PositionRisk
import com.kinetix.risk.model.RunLabel
import com.kinetix.risk.model.RunStatus
import com.kinetix.risk.model.TriggerType
import com.kinetix.risk.model.ValuationJob
import com.kinetix.risk.model.ValuationOutput
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val BASE_DATE = LocalDate.of(2025, 1, 14)
private val TARGET_DATE = LocalDate.of(2025, 1, 15)
private val BASE_TIME = Instant.parse("2025-01-14T10:00:00Z")

private fun completedJob(
    jobId: UUID = UUID.randomUUID(),
    bookId: String = "port-1",
    valuationDate: LocalDate = TARGET_DATE,
    varValue: Double = 5000.0,
) = ValuationJob(
    jobId = jobId,
    bookId = bookId,
    triggerType = TriggerType.ON_DEMAND,
    status = RunStatus.COMPLETED,
    startedAt = BASE_TIME,
    valuationDate = valuationDate,
    completedAt = BASE_TIME.plusMillis(300),
    durationMs = 300,
    calculationType = "PARAMETRIC",
    confidenceLevel = "CL_95",
    varValue = varValue,
    expectedShortfall = varValue * 1.25,
    pvValue = 1_000_000.0,
    delta = 0.6,
    gamma = 0.01,
    vega = 80.0,
    theta = -20.0,
    rho = 0.05,
    positionRiskSnapshot = listOf(
        PositionRisk(
            instrumentId = InstrumentId("AAPL"),
            assetClass = AssetClass.EQUITY,
            marketValue = BigDecimal("50000.00"),
            delta = 0.6,
            gamma = 0.01,
            vega = null,
            varContribution = BigDecimal("2500.00"),
            esContribution = BigDecimal("3125.00"),
            percentageOfTotal = BigDecimal("50.00"),
        ),
    ),
    componentBreakdownSnapshot = listOf(
        ComponentBreakdown(AssetClass.EQUITY, varValue, 100.0),
    ),
    computedOutputsSnapshot = setOf(ValuationOutput.VAR, ValuationOutput.EXPECTED_SHORTFALL),
    triggeredBy = "user-a",
)

class RunComparisonServiceTest : FunSpec({

    val jobRecorder = mockk<ValuationJobRecorder>()
    val differ = SnapshotDiffer()
    val service = RunComparisonService(jobRecorder, differ)

    beforeEach { clearMocks(jobRecorder) }

    test("compareByJobIds loads both jobs and returns structured diff") {
        val baseJobId = UUID.randomUUID()
        val targetJobId = UUID.randomUUID()
        val baseJob = completedJob(jobId = baseJobId, varValue = 5000.0)
        val targetJob = completedJob(jobId = targetJobId, varValue = 7000.0)

        coEvery { jobRecorder.findByJobId(baseJobId) } returns baseJob
        coEvery { jobRecorder.findByJobId(targetJobId) } returns targetJob

        val comparison = service.compareByJobIds(baseJobId, targetJobId)

        comparison.comparisonId shouldNotBe null
        comparison.type shouldBe ComparisonType.RUN_OVER_RUN
        comparison.bookId shouldBe "port-1"
        comparison.baseRun.varValue shouldBe 5000.0
        comparison.targetRun.varValue shouldBe 7000.0
        comparison.portfolioDiff.varChange shouldBe 2000.0
        comparison.attribution shouldBe null
    }

    test("compareByJobIds throws when base job not found") {
        val baseJobId = UUID.randomUUID()
        val targetJobId = UUID.randomUUID()

        coEvery { jobRecorder.findByJobId(baseJobId) } returns null
        coEvery { jobRecorder.findByJobId(targetJobId) } returns completedJob(jobId = targetJobId)

        val ex = shouldThrow<IllegalArgumentException> {
            service.compareByJobIds(baseJobId, targetJobId)
        }
        ex.message shouldBe "Base job not found: $baseJobId"
    }

    test("compareByJobIds throws when target job not found") {
        val baseJobId = UUID.randomUUID()
        val targetJobId = UUID.randomUUID()

        coEvery { jobRecorder.findByJobId(baseJobId) } returns completedJob(jobId = baseJobId)
        coEvery { jobRecorder.findByJobId(targetJobId) } returns null

        val ex = shouldThrow<IllegalArgumentException> {
            service.compareByJobIds(baseJobId, targetJobId)
        }
        ex.message shouldBe "Target job not found: $targetJobId"
    }

    test("compareDayOverDay falls back to latest completed when no Official EOD exists") {
        val baseJob = completedJob(valuationDate = BASE_DATE, varValue = 4500.0)
        val targetJob = completedJob(valuationDate = TARGET_DATE, varValue = 5500.0)

        coEvery { jobRecorder.findOfficialEodByDate("port-1", BASE_DATE) } returns null
        coEvery { jobRecorder.findOfficialEodByDate("port-1", TARGET_DATE) } returns null
        coEvery { jobRecorder.findLatestCompletedByDate("port-1", BASE_DATE) } returns baseJob
        coEvery { jobRecorder.findLatestCompletedByDate("port-1", TARGET_DATE) } returns targetJob

        val comparison = service.compareDayOverDay(
            bookId = "port-1",
            targetDate = TARGET_DATE,
            baseDate = BASE_DATE,
        )

        comparison.bookId shouldBe "port-1"
        comparison.baseRun.varValue shouldBe 4500.0
        comparison.targetRun.varValue shouldBe 5500.0
        comparison.portfolioDiff.varChange shouldBe 1000.0
        comparison.baseRun.label shouldBe "$BASE_DATE"
        comparison.targetRun.label shouldBe "$TARGET_DATE"
    }

    test("compareDayOverDay prefers Official EOD over latest completed") {
        val officialEodJob = completedJob(
            valuationDate = BASE_DATE,
            varValue = 4000.0,
        ).copy(runLabel = RunLabel.OFFICIAL_EOD)
        val latestCompletedJob = completedJob(valuationDate = BASE_DATE, varValue = 4500.0)
        val targetJob = completedJob(valuationDate = TARGET_DATE, varValue = 5500.0)

        coEvery { jobRecorder.findOfficialEodByDate("port-1", BASE_DATE) } returns officialEodJob
        coEvery { jobRecorder.findOfficialEodByDate("port-1", TARGET_DATE) } returns null
        coEvery { jobRecorder.findLatestCompletedByDate("port-1", TARGET_DATE) } returns targetJob

        val comparison = service.compareDayOverDay(
            bookId = "port-1",
            targetDate = TARGET_DATE,
            baseDate = BASE_DATE,
        )

        comparison.baseRun.varValue shouldBe 4000.0
        comparison.baseRun.label shouldBe "Official EOD $BASE_DATE"
        comparison.targetRun.varValue shouldBe 5500.0
        comparison.targetRun.label shouldBe "$TARGET_DATE"
    }

    test("compareDayOverDay labels both runs as Official EOD when both are promoted") {
        val baseEod = completedJob(valuationDate = BASE_DATE, varValue = 4000.0)
            .copy(runLabel = RunLabel.OFFICIAL_EOD)
        val targetEod = completedJob(valuationDate = TARGET_DATE, varValue = 5500.0)
            .copy(runLabel = RunLabel.OFFICIAL_EOD)

        coEvery { jobRecorder.findOfficialEodByDate("port-1", BASE_DATE) } returns baseEod
        coEvery { jobRecorder.findOfficialEodByDate("port-1", TARGET_DATE) } returns targetEod

        val comparison = service.compareDayOverDay(
            bookId = "port-1",
            targetDate = TARGET_DATE,
            baseDate = BASE_DATE,
        )

        comparison.baseRun.label shouldBe "Official EOD $BASE_DATE"
        comparison.targetRun.label shouldBe "Official EOD $TARGET_DATE"
    }

    test("compareDayOverDay throws when no completed job for base date") {
        coEvery { jobRecorder.findOfficialEodByDate("port-1", BASE_DATE) } returns null
        coEvery { jobRecorder.findOfficialEodByDate("port-1", TARGET_DATE) } returns null
        coEvery { jobRecorder.findLatestCompletedByDate("port-1", BASE_DATE) } returns null
        coEvery { jobRecorder.findLatestCompletedByDate("port-1", TARGET_DATE) } returns completedJob(valuationDate = TARGET_DATE)

        val ex = shouldThrow<IllegalArgumentException> {
            service.compareDayOverDay(
                bookId = "port-1",
                targetDate = TARGET_DATE,
                baseDate = BASE_DATE,
            )
        }
        ex.message shouldBe "No completed job for portfolio port-1 on $BASE_DATE"
    }
})
