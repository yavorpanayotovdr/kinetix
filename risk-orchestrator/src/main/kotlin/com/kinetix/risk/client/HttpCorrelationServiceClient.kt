package com.kinetix.risk.client

import com.kinetix.common.model.CorrelationMatrix
import com.kinetix.risk.client.dtos.CorrelationMatrixDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

class HttpCorrelationServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : CorrelationServiceClient {

    override suspend fun getCorrelationMatrix(labels: List<String>, windowDays: Int): ClientResponse<CorrelationMatrix> {
        val labelsParam = labels.joinToString(",")
        val response = httpClient.get("$baseUrl/api/v1/correlations/latest?labels=$labelsParam&window=$windowDays")
        if (response.status == HttpStatusCode.NotFound) return ClientResponse.NotFound(response.status.value)
        val dto: CorrelationMatrixDto = response.body()
        return ClientResponse.Success(dto.toDomain())
    }
}
