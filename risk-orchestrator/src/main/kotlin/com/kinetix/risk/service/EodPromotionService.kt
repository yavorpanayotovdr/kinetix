package com.kinetix.risk.service

import com.kinetix.risk.model.*
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class EodPromotionService(
    private val jobRecorder: ValuationJobRecorder,
    private val eventPublisher: OfficialEodEventPublisher,
    private val meterRegistry: MeterRegistry = SimpleMeterRegistry(),
    private val riskAuditPublisher: RiskAuditEventPublisher? = null,
    private val matViewRefresher: (suspend () -> Unit)? = null,
) {
    private val logger = LoggerFactory.getLogger(EodPromotionService::class.java)

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

            // Emit risk audit event for EOD promotion
            if (riskAuditPublisher != null && promoted.manifestId != null) {
                try {
                    riskAuditPublisher.publish(
                        EodPromotedAuditEvent(
                            jobId = promoted.jobId.toString(),
                            portfolioId = promoted.portfolioId,
                            valuationDate = promoted.valuationDate.toString(),
                            manifestId = promoted.manifestId.toString(),
                            promotedBy = promotedBy,
                            promotedAt = promoted.promotedAt.toString(),
                            varValue = promoted.varValue,
                            expectedShortfall = promoted.expectedShortfall,
                        )
                    )
                } catch (e: Exception) {
                    logger.warn("Failed to publish RISK_RUN_EOD_PROMOTED audit event for job {}", promoted.jobId, e)
                }
            }

            if (matViewRefresher != null) {
                try {
                    matViewRefresher.invoke()
                } catch (e: Exception) {
                    logger.warn("Failed to refresh daily_official_eod_summary materialized view after promoting job {}", promoted.jobId, e)
                }
            }

            sample.stop(meterRegistry.timer("eod.promotion.duration"))
            meterRegistry.counter("eod.promotion.requests", "result", "success").increment()
            meterRegistry.gauge(
                "eod.promotion.last_timestamp",
                listOf(
                    io.micrometer.core.instrument.Tag.of("portfolio_id", promoted.portfolioId),
                ),
                promoted.promotedAt!!.epochSecond.toDouble(),
            )
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
