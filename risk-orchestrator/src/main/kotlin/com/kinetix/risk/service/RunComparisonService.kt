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
    private val manifestRepo: RunManifestRepository? = null,
) {

    suspend fun compareByJobIds(baseJobId: UUID, targetJobId: UUID): RunComparison {
        val baseJob = jobRecorder.findByJobId(baseJobId)
            ?: throw IllegalArgumentException("Base job not found: $baseJobId")
        val targetJob = jobRecorder.findByJobId(targetJobId)
            ?: throw IllegalArgumentException("Target job not found: $targetJobId")

        val baseModelVersion = resolveModelVersion(baseJob.manifestId)
        val targetModelVersion = resolveModelVersion(targetJob.manifestId)
        val base = baseJob.toRunSnapshot("Base", baseModelVersion)
        val target = targetJob.toRunSnapshot("Target", targetModelVersion)
        return compareSnapshots(base, target, ComparisonType.RUN_OVER_RUN, baseJob.portfolioId)
    }

    suspend fun compareDayOverDay(
        portfolioId: String,
        targetDate: LocalDate,
        baseDate: LocalDate,
    ): RunComparison {
        val baseJob = jobRecorder.findOfficialEodByDate(portfolioId, baseDate)
            ?: jobRecorder.findLatestCompletedByDate(portfolioId, baseDate)
            ?: throw IllegalArgumentException("No completed job for portfolio $portfolioId on $baseDate")
        val targetJob = jobRecorder.findOfficialEodByDate(portfolioId, targetDate)
            ?: jobRecorder.findLatestCompletedByDate(portfolioId, targetDate)
            ?: throw IllegalArgumentException("No completed job for portfolio $portfolioId on $targetDate")

        val baseModelVersion = resolveModelVersion(baseJob.manifestId)
        val targetModelVersion = resolveModelVersion(targetJob.manifestId)
        val baseLabel = if (baseJob.runLabel == com.kinetix.risk.model.RunLabel.OFFICIAL_EOD) "Official EOD $baseDate" else "$baseDate"
        val targetLabel = if (targetJob.runLabel == com.kinetix.risk.model.RunLabel.OFFICIAL_EOD) "Official EOD $targetDate" else "$targetDate"
        val base = baseJob.toRunSnapshot(baseLabel, baseModelVersion)
        val target = targetJob.toRunSnapshot(targetLabel, targetModelVersion)
        return compareSnapshots(base, target, ComparisonType.RUN_OVER_RUN, portfolioId)
    }

    private suspend fun resolveModelVersion(manifestId: UUID?): String? {
        if (manifestId == null || manifestRepo == null) return null
        return manifestRepo.findByManifestId(manifestId)?.modelVersion?.ifEmpty { null }
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
