package com.kinetix.gateway.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class StressScenarioDto(
    val id: String,
    val name: String,
    val description: String,
    val shocks: String,
    val status: String,
    val createdBy: String,
    val approvedBy: String? = null,
    val approvedAt: String? = null,
    val createdAt: String,
)

@Serializable
data class CreateScenarioRequestDto(
    val name: String,
    val description: String,
    val shocks: String,
    val createdBy: String,
)

@Serializable
data class ApproveScenarioRequestDto(
    val approvedBy: String,
)

fun StressScenarioDto.toDomain() = StressScenarioItem(
    id = id,
    name = name,
    description = description,
    shocks = shocks,
    status = status,
    createdBy = createdBy,
    approvedBy = approvedBy,
    approvedAt = approvedAt,
    createdAt = createdAt,
)

class HttpRegulatoryServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : RegulatoryServiceClient {

    override suspend fun listScenarios(): List<StressScenarioItem> {
        val response = httpClient.get("$baseUrl/api/v1/stress-scenarios")
        val dtos: List<StressScenarioDto> = response.body()
        return dtos.map { it.toDomain() }
    }

    override suspend fun listApprovedScenarios(): List<StressScenarioItem> {
        val response = httpClient.get("$baseUrl/api/v1/stress-scenarios/approved")
        val dtos: List<StressScenarioDto> = response.body()
        return dtos.map { it.toDomain() }
    }

    override suspend fun createScenario(params: CreateScenarioParams): StressScenarioItem {
        val response = httpClient.post("$baseUrl/api/v1/stress-scenarios") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateScenarioRequestDto(
                    name = params.name,
                    description = params.description,
                    shocks = params.shocks,
                    createdBy = params.createdBy,
                ),
            )
        }
        val dto: StressScenarioDto = response.body()
        return dto.toDomain()
    }

    override suspend fun submitForApproval(id: String): StressScenarioItem {
        val response = httpClient.patch("$baseUrl/api/v1/stress-scenarios/$id/submit")
        val dto: StressScenarioDto = response.body()
        return dto.toDomain()
    }

    override suspend fun approve(id: String, params: ApproveScenarioParams): StressScenarioItem {
        val response = httpClient.patch("$baseUrl/api/v1/stress-scenarios/$id/approve") {
            contentType(ContentType.Application.Json)
            setBody(ApproveScenarioRequestDto(approvedBy = params.approvedBy))
        }
        val dto: StressScenarioDto = response.body()
        return dto.toDomain()
    }

    override suspend fun retire(id: String): StressScenarioItem {
        val response = httpClient.patch("$baseUrl/api/v1/stress-scenarios/$id/retire")
        val dto: StressScenarioDto = response.body()
        return dto.toDomain()
    }

    override suspend fun compareBacktests(baseId: String, targetId: String): JsonObject? {
        val response = httpClient.get("$baseUrl/api/v1/regulatory/backtest/compare") {
            url {
                parameters.append("baseId", baseId)
                parameters.append("targetId", targetId)
            }
        }
        if (response.status == HttpStatusCode.NotFound) return null
        return response.body()
    }
}
