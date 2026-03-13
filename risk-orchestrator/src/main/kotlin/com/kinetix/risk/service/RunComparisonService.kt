package com.kinetix.risk.service

import com.kinetix.risk.mapper.toRunSnapshot
import com.kinetix.risk.model.ComparisonType
import com.kinetix.risk.model.RunComparison
import com.kinetix.risk.model.RunSnapshot
import java.time.LocalDate
import java.util.UUID

class RunComparisonService(
    private val jobRecorder: ValuationJobRecorder,
    private val differ: SnapshotDiffer,
) {

    suspend fun compareByJobIds(baseJobId: UUID, targetJobId: UUID): RunComparison {
        val baseJob = jobRecorder.findByJobId(baseJobId)
            ?: throw IllegalArgumentException("Base job not found: $baseJobId")
        val targetJob = jobRecorder.findByJobId(targetJobId)
            ?: throw IllegalArgumentException("Target job not found: $targetJobId")

        val base = baseJob.toRunSnapshot("Base")
        val target = targetJob.toRunSnapshot("Target")
        return compareSnapshots(base, target, ComparisonType.RUN_OVER_RUN, baseJob.portfolioId)
    }

    suspend fun compareDayOverDay(
        portfolioId: String,
        targetDate: LocalDate,
        baseDate: LocalDate,
    ): RunComparison {
        val baseJob = jobRecorder.findLatestCompletedByDate(portfolioId, baseDate)
            ?: throw IllegalArgumentException("No completed job for portfolio $portfolioId on $baseDate")
        val targetJob = jobRecorder.findLatestCompletedByDate(portfolioId, targetDate)
            ?: throw IllegalArgumentException("No completed job for portfolio $portfolioId on $targetDate")

        val base = baseJob.toRunSnapshot("$baseDate")
        val target = targetJob.toRunSnapshot("$targetDate")
        return compareSnapshots(base, target, ComparisonType.RUN_OVER_RUN, portfolioId)
    }

    fun compareSnapshots(
        base: RunSnapshot,
        target: RunSnapshot,
        type: ComparisonType,
        portfolioId: String,
    ): RunComparison = RunComparison(
        comparisonId = UUID.randomUUID(),
        type = type,
        portfolioId = portfolioId,
        baseRun = base,
        targetRun = target,
        portfolioDiff = differ.computePortfolioDiff(base, target),
        componentDiffs = differ.matchComponents(base.componentBreakdowns, target.componentBreakdowns),
        positionDiffs = differ.matchPositions(base.positionRisks, target.positionRisks),
        parameterDiffs = differ.diffParameters(base, target),
        attribution = null,
    )
}
