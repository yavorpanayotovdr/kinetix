package com.kinetix.regulatory.stress

interface StressTestResultRepository {
    suspend fun save(result: StressTestResult)
    suspend fun findByScenarioId(scenarioId: String): List<StressTestResult>
    suspend fun findByPortfolioId(portfolioId: String): List<StressTestResult>
    suspend fun findById(id: String): StressTestResult?
}
