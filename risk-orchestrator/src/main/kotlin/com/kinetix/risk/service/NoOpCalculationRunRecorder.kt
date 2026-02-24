package com.kinetix.risk.service

import com.kinetix.risk.model.CalculationRun
import java.util.UUID

class NoOpCalculationRunRecorder : CalculationRunRecorder {
    override suspend fun save(run: CalculationRun) {}
    override suspend fun findByPortfolioId(portfolioId: String, limit: Int, offset: Int): List<CalculationRun> = emptyList()
    override suspend fun findByRunId(runId: UUID): CalculationRun? = null
}
