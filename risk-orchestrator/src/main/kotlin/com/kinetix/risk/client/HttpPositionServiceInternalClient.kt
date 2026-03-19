package com.kinetix.risk.client

import com.kinetix.risk.client.dtos.CountResponseDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.time.Instant

class HttpPositionServiceInternalClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : PositionServiceInternalClient {

    override suspend fun countTradeEventsSince(since: Instant): ClientResponse<Long> {
        val response = httpClient.get("$baseUrl/api/v1/internal/trades/count") {
            parameter("since", since.toString())
        }
        if (response.status == HttpStatusCode.NotFound) return ClientResponse.NotFound(response.status.value)
        val dto: CountResponseDto = response.body()
        return ClientResponse.Success(dto.count)
    }
}
