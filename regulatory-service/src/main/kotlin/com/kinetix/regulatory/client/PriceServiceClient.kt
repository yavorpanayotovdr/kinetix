package com.kinetix.regulatory.client

import com.kinetix.regulatory.historical.DailyClosePrice
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

@Serializable
private data class PricePointDto(
    val instrumentId: String,
    val price: PriceAmountDto,
    val timestamp: String,
    val source: String,
)

@Serializable
private data class PriceAmountDto(
    val amount: String,
    val currency: String,
)

interface PriceServiceClient {
    suspend fun fetchDailyClosePrices(instrumentId: String, from: String, to: String): List<DailyClosePrice>
}

class HttpPriceServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : PriceServiceClient {

    override suspend fun fetchDailyClosePrices(
        instrumentId: String,
        from: String,
        to: String,
    ): List<DailyClosePrice> {
        val response = httpClient.get(
            "$baseUrl/api/v1/prices/$instrumentId/history?from=$from&to=$to&interval=1d"
        )
        val points: List<PricePointDto> = response.body()
        return points.map { dto ->
            DailyClosePrice(
                instrumentId = dto.instrumentId,
                date = dto.timestamp.substringBefore("T"),
                closePrice = dto.price.amount.toDouble(),
            )
        }
    }
}
