package com.kinetix.risk.client

import com.kinetix.common.model.BookId
import com.kinetix.common.model.Position
import com.kinetix.risk.client.dtos.BookSummaryDto
import com.kinetix.risk.client.dtos.CounterpartyTradeDto
import com.kinetix.risk.client.dtos.NetCollateralDto
import com.kinetix.risk.client.dtos.PositionDto
import com.kinetix.risk.client.dtos.TradeDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
private data class NetCollateralResponse(
    val counterpartyId: String,
    val collateralReceived: String,
    val collateralPosted: String,
)

class HttpPositionServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : PositionServiceClient {

    override suspend fun getPositions(bookId: BookId): ClientResponse<List<Position>> {
        val response = httpClient.get("$baseUrl/api/v1/books/${bookId.value}/positions")
        if (response.status == HttpStatusCode.NotFound) return ClientResponse.NotFound(response.status.value)
        val dtos: List<PositionDto> = response.body()
        return ClientResponse.Success(dtos.map { it.toDomain() })
    }

    override suspend fun getDistinctBookIds(): ClientResponse<List<BookId>> {
        val response = httpClient.get("$baseUrl/api/v1/books")
        if (response.status == HttpStatusCode.NotFound) return ClientResponse.NotFound(response.status.value)
        val dtos: List<BookSummaryDto> = response.body()
        return ClientResponse.Success(dtos.map { it.toDomain() })
    }

    override suspend fun getTradesInRange(
        bookId: BookId,
        from: Instant,
        to: Instant,
    ): ClientResponse<List<TradeDto>> {
        val response = httpClient.get("$baseUrl/api/v1/books/${bookId.value}/trades")
        if (response.status == HttpStatusCode.NotFound) return ClientResponse.NotFound(response.status.value)
        val allTrades: List<TradeDto> = response.body()
        val filtered = allTrades.filter { dto ->
            val tradedAt = Instant.parse(dto.tradedAt)
            !tradedAt.isBefore(from) && !tradedAt.isAfter(to)
        }
        return ClientResponse.Success(filtered)
    }

    override suspend fun getNetCollateral(counterpartyId: String): ClientResponse<NetCollateralDto> {
        val response = httpClient.get("$baseUrl/api/v1/counterparties/$counterpartyId/collateral/net")
        if (response.status == HttpStatusCode.NotFound) return ClientResponse.NotFound(response.status.value)
        val dto: NetCollateralResponse = response.body()
        return ClientResponse.Success(
            NetCollateralDto(
                collateralReceived = dto.collateralReceived.toDouble(),
                collateralPosted = dto.collateralPosted.toDouble(),
            )
        )
    }

    override suspend fun getInstrumentNettingSets(counterpartyId: String): ClientResponse<Map<String, String>> {
        val response = httpClient.get("$baseUrl/api/v1/counterparties/$counterpartyId/instrument-netting-sets")
        if (response.status == HttpStatusCode.NotFound) return ClientResponse.NotFound(response.status.value)
        val mapping: Map<String, String> = response.body()
        return ClientResponse.Success(mapping)
    }

    override suspend fun getTradesByCounterparty(counterpartyId: String): ClientResponse<List<CounterpartyTradeDto>> {
        val response = httpClient.get("$baseUrl/api/v1/counterparties/$counterpartyId/trades")
        if (response.status == HttpStatusCode.NotFound) return ClientResponse.NotFound(response.status.value)
        val trades: List<CounterpartyTradeDto> = response.body()
        return ClientResponse.Success(trades)
    }
}
