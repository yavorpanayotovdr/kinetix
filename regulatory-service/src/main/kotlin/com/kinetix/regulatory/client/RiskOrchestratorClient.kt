package com.kinetix.regulatory.client

import com.kinetix.regulatory.dto.FrtbResultResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class RiskOrchestratorClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {
    suspend fun calculateFrtb(portfolioId: String): FrtbResultResponse {
        val response = httpClient.post("$baseUrl/api/v1/regulatory/frtb/$portfolioId") {
            contentType(ContentType.Application.Json)
        }
        return response.body()
    }
}
