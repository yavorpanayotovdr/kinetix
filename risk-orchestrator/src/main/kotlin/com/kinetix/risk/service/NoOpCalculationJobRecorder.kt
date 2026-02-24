package com.kinetix.risk.service

import com.kinetix.risk.model.CalculationJob
import java.util.UUID

class NoOpCalculationJobRecorder : CalculationJobRecorder {
    override suspend fun save(job: CalculationJob) {}
    override suspend fun findByPortfolioId(portfolioId: String, limit: Int, offset: Int): List<CalculationJob> = emptyList()
    override suspend fun findByJobId(jobId: UUID): CalculationJob? = null
}
