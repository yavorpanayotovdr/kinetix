package com.kinetix.risk.service

import com.kinetix.risk.model.CalculationRun
import java.util.UUID

interface CalculationRunRecorder {
    suspend fun save(run: CalculationRun)
    suspend fun findByPortfolioId(portfolioId: String, limit: Int = 50, offset: Int = 0): List<CalculationRun>
    suspend fun findByRunId(runId: UUID): CalculationRun?
}
