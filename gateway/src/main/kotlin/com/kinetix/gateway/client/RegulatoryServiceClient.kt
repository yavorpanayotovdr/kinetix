package com.kinetix.gateway.client

data class StressScenarioItem(
    val id: String,
    val name: String,
    val description: String,
    val shocks: String,
    val status: String,
    val createdBy: String,
    val approvedBy: String?,
    val approvedAt: String?,
    val createdAt: String,
)

data class CreateScenarioParams(
    val name: String,
    val description: String,
    val shocks: String,
    val createdBy: String,
)

data class ApproveScenarioParams(
    val approvedBy: String,
)

interface RegulatoryServiceClient {
    suspend fun listScenarios(): List<StressScenarioItem>
    suspend fun listApprovedScenarios(): List<StressScenarioItem>
    suspend fun createScenario(params: CreateScenarioParams): StressScenarioItem
    suspend fun submitForApproval(id: String): StressScenarioItem
    suspend fun approve(id: String, params: ApproveScenarioParams): StressScenarioItem
    suspend fun retire(id: String): StressScenarioItem
    suspend fun compareBacktests(baseId: String, targetId: String): kotlinx.serialization.json.JsonObject?
}
