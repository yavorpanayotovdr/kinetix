package com.kinetix.risk.service

import com.kinetix.risk.model.*
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class EodPromotionService(
    private val jobRecorder: ValuationJobRecorder,
    private val eventPublisher: OfficialEodEventPublisher,
    private val meterRegistry: MeterRegistry = SimpleMeterRegistry(),
) {

    suspend fun promoteToOfficialEod(jobId: UUID, promotedBy: String): ValuationJob {
        val sample = Timer.start(meterRegistry)
        try {
            val job = jobRecorder.findByJobId(jobId)
                ?: throw EodPromotionException.JobNotFound(jobId)

            if (job.status != RunStatus.COMPLETED) {
                throw EodPromotionException.JobNotCompleted(jobId)
            }

            if (job.promotedAt != null) {
                throw EodPromotionException.AlreadyPromoted(jobId)
            }

            if (job.triggeredBy != null && job.triggeredBy == promotedBy) {
                throw EodPromotionException.SelfPromotion(promotedBy)
            }

            // Supersede existing Official EOD for same portfolio/date if one exists
            val existingEod = jobRecorder.findOfficialEodByDate(job.portfolioId, job.valuationDate)
            if (existingEod != null && existingEod.jobId != jobId) {
                jobRecorder.supersedeOfficialEod(existingEod.jobId)
            }

            val promoted = jobRecorder.promoteToOfficialEod(jobId, promotedBy, Instant.now())

            eventPublisher.publish(
                OfficialEodPromotedEvent(
                    jobId = promoted.jobId.toString(),
                    portfolioId = promoted.portfolioId,
                    valuationDate = promoted.valuationDate.toString(),
                    promotedBy = promotedBy,
                    promotedAt = promoted.promotedAt.toString(),
                    varValue = promoted.varValue,
                    expectedShortfall = promoted.expectedShortfall,
                )
            )

            sample.stop(meterRegistry.timer("eod.promotion.duration"))
            meterRegistry.counter("eod.promotion.requests", "result", "success").increment()
            return promoted
        } catch (e: Exception) {
            sample.stop(meterRegistry.timer("eod.promotion.duration"))
            meterRegistry.counter("eod.promotion.requests", "result", "error").increment()
            throw e
        }
    }

    suspend fun demoteFromOfficialEod(jobId: UUID, demotedBy: String): ValuationJob {
        val job = jobRecorder.findByJobId(jobId)
            ?: throw EodPromotionException.JobNotFound(jobId)

        if (job.runLabel != RunLabel.OFFICIAL_EOD || job.promotedAt == null) {
            throw EodPromotionException.NotPromoted(jobId)
        }

        return jobRecorder.demoteOfficialEod(jobId)
    }

    suspend fun findOfficialEod(portfolioId: String, valuationDate: LocalDate): ValuationJob? {
        return jobRecorder.findOfficialEodByDate(portfolioId, valuationDate)
    }
}
