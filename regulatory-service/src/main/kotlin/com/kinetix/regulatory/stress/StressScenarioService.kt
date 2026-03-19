package com.kinetix.regulatory.stress

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class StressScenarioService(
    private val repository: StressScenarioRepository,
    private val resultRepository: StressTestResultRepository? = null,
) {

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

    suspend fun runScenario(scenarioId: String, portfolioId: String, modelVersion: String?): StressTestResult {
        val scenario = findOrThrow(scenarioId)
        if (scenario.status != ScenarioStatus.APPROVED) {
            throw IllegalStateException("Can only run APPROVED scenarios, current: ${scenario.status}")
        }

        val pnlImpact = computePnlImpact(scenario.shocks)
        val result = StressTestResult(
            id = UUID.randomUUID().toString(),
            scenarioId = scenarioId,
            portfolioId = portfolioId,
            calculatedAt = Instant.now(),
            basePv = null,
            stressedPv = null,
            pnlImpact = pnlImpact,
            varImpact = null,
            positionImpacts = null,
            modelVersion = modelVersion,
        )

        resultRepository?.save(result)
        return result
    }

    private fun computePnlImpact(shocksJson: String): BigDecimal {
        val parsed = runCatching { Json.parseToJsonElement(shocksJson) as? JsonObject }.getOrNull()
            ?: return BigDecimal.ZERO
        val totalShock = parsed.values.sumOf { element ->
            runCatching { element.jsonPrimitive.double }.getOrDefault(0.0)
        }
        return BigDecimal.valueOf(totalShock)
    }

    private suspend fun findOrThrow(id: String): StressScenario =
        repository.findById(id) ?: throw NoSuchElementException("Stress scenario not found: $id")
}
