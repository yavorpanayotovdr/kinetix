package com.kinetix.risk.service

import com.kinetix.risk.model.ValuationJob
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class NoOpValuationJobRecorder : ValuationJobRecorder {
    override suspend fun save(job: ValuationJob) {}
    override suspend fun update(job: ValuationJob) {}
    override suspend fun findByPortfolioId(portfolioId: String, limit: Int, offset: Int, from: Instant?, to: Instant?, valuationDate: LocalDate?): List<ValuationJob> = emptyList()
    override suspend fun countByPortfolioId(portfolioId: String, from: Instant?, to: Instant?, valuationDate: LocalDate?): Long = 0
    override suspend fun findByJobId(jobId: UUID): ValuationJob? = null
    override suspend fun findDistinctPortfolioIds(): List<String> = emptyList()
    override suspend fun findLatestCompletedByDate(portfolioId: String, valuationDate: LocalDate): ValuationJob? = null
    override suspend fun findLatestCompleted(portfolioId: String): ValuationJob? = null
    override suspend fun findLatestCompletedBeforeDate(portfolioId: String, beforeDate: LocalDate): ValuationJob? = null
}
