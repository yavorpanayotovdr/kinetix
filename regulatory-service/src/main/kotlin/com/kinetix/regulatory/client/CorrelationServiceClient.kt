package com.kinetix.regulatory.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

data class CorrelationMatrix(
    val labels: List<String>,
    val values: List<Double>,
)

@Serializable
private data class CorrelationMatrixDto(
    val labels: List<String>,
    val values: List<Double>,
    val windowDays: Int,
    val asOfDate: String,
    val method: String,
)

interface CorrelationServiceClient {
    suspend fun fetchLatestMatrix(assetClasses: List<String>): CorrelationMatrix?
}

class HttpCorrelationServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : CorrelationServiceClient {

    override suspend fun fetchLatestMatrix(assetClasses: List<String>): CorrelationMatrix? {
        val labels = assetClasses.joinToString(",")
        val response = httpClient.get("$baseUrl/api/v1/correlations/latest?labels=$labels&window=252")
        if (response.status.value == 404) return null
        val dto: CorrelationMatrixDto = response.body()
        return CorrelationMatrix(labels = dto.labels, values = dto.values)
    }
}
