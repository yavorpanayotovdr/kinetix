package com.kinetix.risk.client

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PricePoint
import com.kinetix.risk.client.dtos.PricePointDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.time.Instant

class HttpPriceServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : PriceServiceClient {

    override suspend fun getLatestPrice(instrumentId: InstrumentId): ClientResponse<PricePoint> {
        val response = httpClient.get("$baseUrl/api/v1/prices/${instrumentId.value}/latest")
        if (response.status == HttpStatusCode.NotFound) return ClientResponse.NotFound(response.status.value)
        val dto: PricePointDto = response.body()
        return ClientResponse.Success(dto.toDomain())
    }

    override suspend fun getPriceHistory(
        instrumentId: InstrumentId,
        from: Instant,
        to: Instant,
    ): ClientResponse<List<PricePoint>> {
        val response = httpClient.get("$baseUrl/api/v1/prices/${instrumentId.value}/history") {
            parameter("from", from.toString())
            parameter("to", to.toString())
        }
        if (response.status == HttpStatusCode.NotFound) return ClientResponse.NotFound(response.status.value)
        val dtos: List<PricePointDto> = response.body()
        return ClientResponse.Success(dtos.map { it.toDomain() })
    }
}
