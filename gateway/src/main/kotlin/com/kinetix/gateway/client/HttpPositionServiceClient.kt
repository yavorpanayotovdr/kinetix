package com.kinetix.gateway.client

import com.kinetix.common.model.PortfolioId
import com.kinetix.common.model.Position
import com.kinetix.common.model.Trade
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class HttpPositionServiceClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : PositionServiceClient {

    override suspend fun listPortfolios(): List<PortfolioSummary> {
        val response = httpClient.get("$baseUrl/api/v1/portfolios")
        val dtos: List<PortfolioSummaryDto> = response.body()
        return dtos.map { it.toDomain() }
    }

    override suspend fun bookTrade(command: BookTradeCommand): BookTradeResult {
        val response = httpClient.post("$baseUrl/api/v1/portfolios/${command.portfolioId.value}/trades") {
            contentType(ContentType.Application.Json)
            setBody(
                BookTradeRequestDto(
                    tradeId = command.tradeId.value,
                    instrumentId = command.instrumentId.value,
                    assetClass = command.assetClass.name,
                    side = command.side.name,
                    quantity = command.quantity.toPlainString(),
                    priceAmount = command.price.amount.toPlainString(),
                    priceCurrency = command.price.currency.currencyCode,
                    tradedAt = command.tradedAt.toString(),
                )
            )
        }
        val dto: BookTradeResponseDto = response.body()
        return dto.toDomain()
    }

    override suspend fun getPositions(portfolioId: PortfolioId): List<Position> {
        val response = httpClient.get("$baseUrl/api/v1/portfolios/${portfolioId.value}/positions")
        val dtos: List<PositionDto> = response.body()
        return dtos.map { it.toDomain() }
    }

    override suspend fun getTradeHistory(portfolioId: PortfolioId): List<Trade> {
        val response = httpClient.get("$baseUrl/api/v1/portfolios/${portfolioId.value}/trades")
        val dtos: List<TradeDto> = response.body()
        return dtos.map { it.toDomain() }
    }

    override suspend fun getPortfolioSummary(portfolioId: PortfolioId, baseCurrency: String): PortfolioAggregationSummary {
        val response = httpClient.get("$baseUrl/api/v1/portfolios/${portfolioId.value}/summary") {
            parameter("baseCurrency", baseCurrency)
        }
        val dto: PortfolioAggregationDto = response.body()
        return dto.toDomain()
    }
}
