package com.kinetix.regulatory.stress

import java.time.Instant
import java.util.UUID

class StressScenarioService(private val repository: StressScenarioRepository) {

    suspend fun create(
        name: String,
        description: String,
        shocks: String,
        createdBy: String,
    ): StressScenario {
        val scenario = StressScenario(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            shocks = shocks,
            status = ScenarioStatus.DRAFT,
            createdBy = createdBy,
            approvedBy = null,
            approvedAt = null,
            createdAt = Instant.now(),
        )
        repository.save(scenario)
        return scenario
    }

    suspend fun submitForApproval(id: String): StressScenario {
        val scenario = findOrThrow(id)
        if (scenario.status != ScenarioStatus.DRAFT) {
            throw IllegalStateException("Can only submit for approval from DRAFT status, current: ${scenario.status}")
        }
        val updated = scenario.copy(status = ScenarioStatus.PENDING_APPROVAL)
        repository.save(updated)
        return updated
    }

    suspend fun approve(id: String, approvedBy: String): StressScenario {
        val scenario = findOrThrow(id)
        if (scenario.status != ScenarioStatus.PENDING_APPROVAL) {
            throw IllegalStateException("Can only approve from PENDING_APPROVAL status, current: ${scenario.status}")
        }
        val updated = scenario.copy(
            status = ScenarioStatus.APPROVED,
            approvedBy = approvedBy,
            approvedAt = Instant.now(),
        )
        repository.save(updated)
        return updated
    }

    suspend fun retire(id: String): StressScenario {
        val scenario = findOrThrow(id)
        if (scenario.status != ScenarioStatus.APPROVED) {
            throw IllegalStateException("Can only retire from APPROVED status, current: ${scenario.status}")
        }
        val updated = scenario.copy(status = ScenarioStatus.RETIRED)
        repository.save(updated)
        return updated
    }

    suspend fun listAll(): List<StressScenario> = repository.findAll()

    suspend fun listApproved(): List<StressScenario> = repository.findByStatus(ScenarioStatus.APPROVED)

    suspend fun findById(id: String): StressScenario? = repository.findById(id)

    private suspend fun findOrThrow(id: String): StressScenario =
        repository.findById(id) ?: throw NoSuchElementException("Stress scenario not found: $id")
}
