package com.kinetix.risk.service

import com.kinetix.risk.model.ValuationJob
import java.time.Instant
import java.util.UUID

class NoOpValuationJobRecorder : ValuationJobRecorder {
    override suspend fun save(job: ValuationJob) {}
    override suspend fun findByPortfolioId(portfolioId: String, limit: Int, offset: Int, from: Instant?, to: Instant?): List<ValuationJob> = emptyList()
    override suspend fun findByJobId(jobId: UUID): ValuationJob? = null
}
