package com.kinetix.risk.service

import com.kinetix.risk.model.ValuationJob
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface ValuationJobRecorder {
    suspend fun save(job: ValuationJob)
    suspend fun update(job: ValuationJob)
    suspend fun findByPortfolioId(
        portfolioId: String,
        limit: Int = 50,
        offset: Int = 0,
        from: Instant? = null,
        to: Instant? = null,
        valuationDate: LocalDate? = null,
    ): List<ValuationJob>
    suspend fun countByPortfolioId(
        portfolioId: String,
        from: Instant? = null,
        to: Instant? = null,
        valuationDate: LocalDate? = null,
    ): Long
    suspend fun findByJobId(jobId: UUID): ValuationJob?
    suspend fun findDistinctPortfolioIds(): List<String>
    suspend fun findLatestCompletedByDate(portfolioId: String, valuationDate: LocalDate): ValuationJob?
    suspend fun findLatestCompleted(portfolioId: String): ValuationJob?
}
