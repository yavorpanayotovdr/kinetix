package com.kinetix.gateway.client

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.MarketDataPoint
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.time.Instant

class HttpMarketDataServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : MarketDataServiceClient {

    override suspend fun getLatestPrice(instrumentId: InstrumentId): MarketDataPoint? {
        val response = httpClient.get("$baseUrl/api/v1/market-data/${instrumentId.value}/latest")
        if (response.status == HttpStatusCode.NotFound) return null
        val dto: MarketDataPointDto = response.body()
        return dto.toDomain()
    }

    override suspend fun getPriceHistory(
        instrumentId: InstrumentId,
        from: Instant,
        to: Instant,
    ): List<MarketDataPoint> {
        val response = httpClient.get("$baseUrl/api/v1/market-data/${instrumentId.value}/history") {
            parameter("from", from.toString())
            parameter("to", to.toString())
        }
        val dtos: List<MarketDataPointDto> = response.body()
        return dtos.map { it.toDomain() }
    }
}
