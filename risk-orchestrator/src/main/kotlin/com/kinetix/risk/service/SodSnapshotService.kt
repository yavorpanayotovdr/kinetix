package com.kinetix.risk.service

import com.kinetix.common.model.BookId
import com.kinetix.risk.cache.VaRCache
import com.kinetix.risk.client.PositionProvider
import com.kinetix.risk.model.*
import com.kinetix.risk.persistence.DailyRiskSnapshotRepository
import com.kinetix.risk.persistence.SodBaselineRepository
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class SodSnapshotService(
    private val sodBaselineRepository: SodBaselineRepository,
    private val dailyRiskSnapshotRepository: DailyRiskSnapshotRepository,
    private val varCache: VaRCache,
    private val varCalculationService: VaRCalculationService,
    private val positionProvider: PositionProvider,
    private val jobRecorder: ValuationJobRecorder? = null,
    private val maxCacheAgeMinutes: Long = 120,
) {
    private val logger = LoggerFactory.getLogger(SodSnapshotService::class.java)

    suspend fun createSnapshot(
        bookId: BookId,
        snapshotType: SnapshotType,
        valuationResult: ValuationResult? = null,
        date: LocalDate = LocalDate.now(),
    ) {
        val result = valuationResult
            ?: varCache.get(bookId.value)?.takeIf { isFreshEnough(it) }
            ?: calculateFreshVaR(bookId)
            ?: throw IllegalStateException("Cannot create SOD snapshot: no valuation data available for ${bookId.value}")

        val positions = positionProvider.getPositions(bookId)

        val snapshots = result.positionRisk.map { risk ->
            val position = positions.find { it.instrumentId == risk.instrumentId }
            DailyRiskSnapshot(
                bookId = bookId,
                snapshotDate = date,
                instrumentId = risk.instrumentId,
                assetClass = risk.assetClass,
                quantity = position?.quantity ?: BigDecimal.ONE,
                marketPrice = position?.marketPrice?.amount ?: risk.marketValue,
                delta = risk.delta,
                gamma = risk.gamma,
                vega = risk.vega,
                theta = result.greeks?.theta,
                rho = result.greeks?.rho,
            )
        }

        dailyRiskSnapshotRepository.saveAll(snapshots)

        val baseline = SodBaseline(
            bookId = bookId,
            baselineDate = date,
            snapshotType = snapshotType,
            createdAt = Instant.now(),
            sourceJobId = result.jobId,
            calculationType = result.calculationType.name,
            varValue = result.varValue,
            expectedShortfall = result.expectedShortfall,
        )
        sodBaselineRepository.save(baseline)

        logger.info(
            "SOD snapshot created for portfolio {} on {} ({}, {} positions)",
            bookId.value, date, snapshotType, snapshots.size,
        )
    }

    suspend fun getBaselineStatus(bookId: BookId, date: LocalDate): SodBaselineStatus {
        val baseline = sodBaselineRepository.findByBookIdAndDate(bookId, date)
        return if (baseline != null) {
            SodBaselineStatus(
                exists = true,
                baselineDate = baseline.baselineDate.toString(),
                snapshotType = baseline.snapshotType,
                createdAt = baseline.createdAt,
                sourceJobId = baseline.sourceJobId?.toString(),
                calculationType = baseline.calculationType,
            )
        } else {
            SodBaselineStatus(exists = false)
        }
    }

    suspend fun createSnapshotFromJob(
        bookId: BookId,
        jobId: UUID,
        date: LocalDate = LocalDate.now(),
    ) {
        val recorder = jobRecorder
            ?: throw IllegalStateException("Job recorder is not configured")
        val job = recorder.findByJobId(jobId)
            ?: throw IllegalArgumentException("Valuation job $jobId not found")
        require(job.status == RunStatus.COMPLETED) {
            "Valuation job $jobId is not completed (status: ${job.status})"
        }
        require(job.bookId == bookId.value) {
            "Valuation job $jobId belongs to portfolio ${job.bookId}, not ${bookId.value}"
        }

        val calcType = CalculationType.valueOf(job.calculationType ?: "PARAMETRIC")
        val confLevel = ConfidenceLevel.valueOf(job.confidenceLevel ?: "CL_95")
        val request = VaRCalculationRequest(
            bookId = bookId,
            calculationType = calcType,
            confidenceLevel = confLevel,
            requestedOutputs = ValuationOutput.entries.toSet(),
        )
        val result = varCalculationService.calculateVaR(request, TriggerType.SCHEDULED, triggeredBy = "SYSTEM")
            ?: throw IllegalStateException("Re-calculation failed for job $jobId parameters")

        createSnapshot(bookId, SnapshotType.MANUAL, result, date)
    }

    suspend fun resetBaseline(bookId: BookId, date: LocalDate) {
        dailyRiskSnapshotRepository.deleteByBookIdAndDate(bookId, date)
        sodBaselineRepository.deleteByBookIdAndDate(bookId, date)
        logger.info("SOD baseline reset for portfolio {} on {}", bookId.value, date)
    }

    private suspend fun calculateFreshVaR(bookId: BookId): ValuationResult? {
        logger.info("No cached VaR for {}, triggering fresh calculation for SOD snapshot", bookId.value)
        val request = VaRCalculationRequest(
            bookId = bookId,
            calculationType = CalculationType.PARAMETRIC,
            confidenceLevel = ConfidenceLevel.CL_95,
            requestedOutputs = ValuationOutput.entries.toSet(),
        )
        return varCalculationService.calculateVaR(request, TriggerType.SCHEDULED, runLabel = RunLabel.SOD, triggeredBy = "SYSTEM")
    }

    private fun isFreshEnough(result: ValuationResult): Boolean {
        val age = java.time.Duration.between(result.calculatedAt, Instant.now())
        val fresh = age.toMinutes() <= maxCacheAgeMinutes
        if (!fresh) {
            logger.info("Cached VaR is {}min old (max {}min), will recalculate", age.toMinutes(), maxCacheAgeMinutes)
        }
        return fresh
    }
}
