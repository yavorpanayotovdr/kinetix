package com.kinetix.risk.client

import com.kinetix.common.model.BookId
import com.kinetix.common.model.Position
import com.kinetix.risk.client.dtos.BookSummaryDto
import com.kinetix.risk.client.dtos.PositionDto
import com.kinetix.risk.client.dtos.TradeDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.time.Instant

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
}
