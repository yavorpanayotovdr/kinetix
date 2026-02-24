package com.kinetix.risk.service

import com.kinetix.risk.model.ValuationJob
import java.util.UUID

interface ValuationJobRecorder {
    suspend fun save(job: ValuationJob)
    suspend fun findByPortfolioId(portfolioId: String, limit: Int = 50, offset: Int = 0): List<ValuationJob>
    suspend fun findByJobId(jobId: UUID): ValuationJob?
}
