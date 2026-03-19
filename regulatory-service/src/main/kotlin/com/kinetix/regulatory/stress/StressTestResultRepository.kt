package com.kinetix.regulatory.stress

interface StressTestResultRepository {
    suspend fun save(result: StressTestResult)
    suspend fun findByScenarioId(scenarioId: String): List<StressTestResult>
    suspend fun findByBookId(bookId: String): List<StressTestResult>
    suspend fun findById(id: String): StressTestResult?
}
