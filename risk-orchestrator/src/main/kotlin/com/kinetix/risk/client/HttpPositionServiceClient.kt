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
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
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

    private val logger = LoggerFactory.getLogger(HttpPositionServiceClient::class.java)

    @Serializable
    private data class UpstreamErrorBody(val code: String = "", val message: String = "")

    private val lenientJson = Json { ignoreUnknownKeys = true }

    private suspend fun errorResponseFor(response: HttpResponse): ClientResponse<Nothing> {
        val body = try {
            response.bodyAsText()
        } catch (_: Exception) {
            ""
        }
        val message = try {
            lenientJson.decodeFromString<UpstreamErrorBody>(body).message
        } catch (_: Exception) {
            body.ifBlank { response.status.description }
        }
        return if (response.status == HttpStatusCode.ServiceUnavailable) {
            ClientResponse.ServiceUnavailable()
        } else {
            ClientResponse.UpstreamError(response.status.value, message)
        }
    }

    override suspend fun getPositions(bookId: BookId): ClientResponse<List<Position>> = try {
        val response = httpClient.get("$baseUrl/api/v1/books/${bookId.value}/positions")
        when {
            response.status == HttpStatusCode.NotFound -> ClientResponse.NotFound(response.status.value)
            response.status.isSuccess() -> ClientResponse.Success(response.body<List<PositionDto>>().map { it.toDomain() })
            else -> errorResponseFor(response)
        }
    } catch (e: Exception) {
        logger.warn("Network error fetching positions for book {}", bookId.value, e)
        ClientResponse.NetworkError(e)
    }

    override suspend fun getDistinctBookIds(): ClientResponse<List<BookId>> = try {
        val response = httpClient.get("$baseUrl/api/v1/books")
        when {
            response.status == HttpStatusCode.NotFound -> ClientResponse.NotFound(response.status.value)
            response.status.isSuccess() -> ClientResponse.Success(response.body<List<BookSummaryDto>>().map { it.toDomain() })
            else -> errorResponseFor(response)
        }
    } catch (e: Exception) {
        logger.warn("Network error fetching distinct book IDs", e)
        ClientResponse.NetworkError(e)
    }

    override suspend fun getTradesInRange(
        bookId: BookId,
        from: Instant,
        to: Instant,
    ): ClientResponse<List<TradeDto>> = try {
        val response = httpClient.get("$baseUrl/api/v1/books/${bookId.value}/trades")
        when {
            response.status == HttpStatusCode.NotFound -> ClientResponse.NotFound(response.status.value)
            response.status.isSuccess() -> {
                val allTrades: List<TradeDto> = response.body()
                val filtered = allTrades.filter { dto ->
                    val tradedAt = Instant.parse(dto.tradedAt)
                    !tradedAt.isBefore(from) && !tradedAt.isAfter(to)
                }
                ClientResponse.Success(filtered)
            }
            else -> errorResponseFor(response)
        }
    } catch (e: Exception) {
        logger.warn("Network error fetching trades for book {}", bookId.value, e)
        ClientResponse.NetworkError(e)
    }

    override suspend fun getNetCollateral(counterpartyId: String): ClientResponse<NetCollateralDto> = try {
        val response = httpClient.get("$baseUrl/api/v1/counterparties/$counterpartyId/collateral/net")
        when {
            response.status == HttpStatusCode.NotFound -> ClientResponse.NotFound(response.status.value)
            response.status.isSuccess() -> {
                val dto: NetCollateralResponse = response.body()
                ClientResponse.Success(
                    NetCollateralDto(
                        collateralReceived = dto.collateralReceived.toDouble(),
                        collateralPosted = dto.collateralPosted.toDouble(),
                    )
                )
            }
            else -> errorResponseFor(response)
        }
    } catch (e: Exception) {
        logger.warn("Network error fetching net collateral for counterparty {}", counterpartyId, e)
        ClientResponse.NetworkError(e)
    }

    override suspend fun getInstrumentNettingSets(counterpartyId: String): ClientResponse<Map<String, String>> = try {
        val response = httpClient.get("$baseUrl/api/v1/counterparties/$counterpartyId/instrument-netting-sets")
        when {
            response.status == HttpStatusCode.NotFound -> ClientResponse.NotFound(response.status.value)
            response.status.isSuccess() -> ClientResponse.Success(response.body())
            else -> errorResponseFor(response)
        }
    } catch (e: Exception) {
        logger.warn("Network error fetching netting sets for counterparty {}", counterpartyId, e)
        ClientResponse.NetworkError(e)
    }

    override suspend fun getTradesByCounterparty(counterpartyId: String): ClientResponse<List<CounterpartyTradeDto>> = try {
        val response = httpClient.get("$baseUrl/api/v1/counterparties/$counterpartyId/trades")
        when {
            response.status == HttpStatusCode.NotFound -> ClientResponse.NotFound(response.status.value)
            response.status.isSuccess() -> ClientResponse.Success(response.body())
            else -> errorResponseFor(response)
        }
    } catch (e: Exception) {
        logger.warn("Network error fetching trades for counterparty {}", counterpartyId, e)
        ClientResponse.NetworkError(e)
    }
}
