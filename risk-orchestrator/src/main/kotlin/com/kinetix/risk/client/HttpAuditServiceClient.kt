package com.kinetix.risk.client

import com.kinetix.risk.client.dtos.CountResponseDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.time.Instant

class HttpAuditServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : AuditServiceClient {

    override suspend fun countAuditEventsSince(since: Instant): ClientResponse<Long> {
        val response = httpClient.get("$baseUrl/api/v1/internal/audit/count") {
            parameter("since", since.toString())
        }
        if (response.status == HttpStatusCode.NotFound) return ClientResponse.NotFound(response.status.value)
        val dto: CountResponseDto = response.body()
        return ClientResponse.Success(dto.count)
    }
}
