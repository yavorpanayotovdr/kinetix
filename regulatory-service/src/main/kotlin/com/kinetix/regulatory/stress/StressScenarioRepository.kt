package com.kinetix.regulatory.stress

interface StressScenarioRepository {
    suspend fun save(scenario: StressScenario)
    suspend fun findById(id: String): StressScenario?
    suspend fun findAll(): List<StressScenario>
    suspend fun findByStatus(status: ScenarioStatus): List<StressScenario>
}
