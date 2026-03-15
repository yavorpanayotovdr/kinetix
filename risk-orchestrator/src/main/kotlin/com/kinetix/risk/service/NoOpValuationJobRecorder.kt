package com.kinetix.risk.service

import com.kinetix.risk.model.JobPhaseName
import com.kinetix.risk.model.RunLabel
import com.kinetix.risk.model.ValuationJob
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class NoOpValuationJobRecorder : ValuationJobRecorder {
    override suspend fun save(job: ValuationJob) {}
    override suspend fun update(job: ValuationJob) {}
    override suspend fun updateCurrentPhase(jobId: UUID, phase: JobPhaseName) {}
    override suspend fun findByPortfolioId(portfolioId: String, limit: Int, offset: Int, from: Instant?, to: Instant?, valuationDate: LocalDate?, runLabel: RunLabel?): List<ValuationJob> = emptyList()
    override suspend fun countByPortfolioId(portfolioId: String, from: Instant?, to: Instant?, valuationDate: LocalDate?, runLabel: RunLabel?): Long = 0
    override suspend fun findByJobId(jobId: UUID): ValuationJob? = null
    override suspend fun findDistinctPortfolioIds(): List<String> = emptyList()
    override suspend fun findLatestCompletedByDate(portfolioId: String, valuationDate: LocalDate): ValuationJob? = null
    override suspend fun findLatestCompleted(portfolioId: String): ValuationJob? = null
    override suspend fun findLatestCompletedBeforeDate(portfolioId: String, beforeDate: LocalDate): ValuationJob? = null
    override suspend fun findOfficialEodByDate(portfolioId: String, valuationDate: LocalDate): ValuationJob? = null
    override suspend fun findOfficialEodRange(portfolioId: String, from: LocalDate, to: LocalDate): List<ValuationJob> = emptyList()
    override suspend fun promoteToOfficialEod(jobId: UUID, promotedBy: String, promotedAt: Instant): ValuationJob =
        throw UnsupportedOperationException("No-op recorder does not support EOD promotion")
    override suspend fun demoteOfficialEod(jobId: UUID): ValuationJob =
        throw UnsupportedOperationException("No-op recorder does not support EOD demotion")
    override suspend fun supersedeOfficialEod(jobId: UUID): ValuationJob =
        throw UnsupportedOperationException("No-op recorder does not support EOD supersession")
}
