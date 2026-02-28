package com.kinetix.risk.client

import com.kinetix.common.model.PortfolioId
import com.kinetix.common.model.Position
import com.kinetix.risk.client.dtos.PortfolioSummaryDto
import com.kinetix.risk.client.dtos.PositionDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class HttpPositionServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : PositionServiceClient {

    override suspend fun getPositions(portfolioId: PortfolioId): ClientResponse<List<Position>> {
        val response = httpClient.get("$baseUrl/api/v1/portfolios/${portfolioId.value}/positions")
        if (response.status == HttpStatusCode.NotFound) return ClientResponse.NotFound(response.status.value)
        val dtos: List<PositionDto> = response.body()
        return ClientResponse.Success(dtos.map { it.toDomain() })
    }

    override suspend fun getDistinctPortfolioIds(): ClientResponse<List<PortfolioId>> {
        val response = httpClient.get("$baseUrl/api/v1/portfolios")
        if (response.status == HttpStatusCode.NotFound) return ClientResponse.NotFound(response.status.value)
        val dtos: List<PortfolioSummaryDto> = response.body()
        return ClientResponse.Success(dtos.map { it.toDomain() })
    }
}
