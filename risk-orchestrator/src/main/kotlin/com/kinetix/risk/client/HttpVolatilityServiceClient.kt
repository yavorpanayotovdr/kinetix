package com.kinetix.risk.client

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.VolSurface
import com.kinetix.risk.client.dtos.VolSurfaceDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

class HttpVolatilityServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : VolatilityServiceClient {

    override suspend fun getLatestSurface(instrumentId: InstrumentId): ClientResponse<VolSurface> {
        val response = httpClient.get("$baseUrl/api/v1/volatility/${instrumentId.value}/surface/latest")
        if (response.status == HttpStatusCode.NotFound) return ClientResponse.NotFound(response.status.value)
        val dto: VolSurfaceDto = response.body()
        return ClientResponse.Success(dto.toDomain())
    }
}
