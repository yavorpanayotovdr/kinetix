package com.kinetix.risk.service

import com.kinetix.risk.model.ValuationJob
import java.time.Instant
import java.util.UUID

interface ValuationJobRecorder {
    suspend fun save(job: ValuationJob)
    suspend fun findByPortfolioId(
        portfolioId: String,
        limit: Int = 50,
        offset: Int = 0,
        from: Instant? = null,
        to: Instant? = null,
    ): List<ValuationJob>
    suspend fun findByJobId(jobId: UUID): ValuationJob?
}
