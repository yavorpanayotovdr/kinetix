package com.kinetix.risk.service

import com.kinetix.risk.model.CalculationJob
import java.util.UUID

interface CalculationJobRecorder {
    suspend fun save(job: CalculationJob)
    suspend fun findByPortfolioId(portfolioId: String, limit: Int = 50, offset: Int = 0): List<CalculationJob>
    suspend fun findByJobId(jobId: UUID): CalculationJob?
}
