package com.kinetix.gateway.client

import com.kinetix.common.model.*
import java.math.BigDecimal
import java.time.Instant

data class BookTradeCommand(
    val tradeId: TradeId,
    val portfolioId: PortfolioId,
    val instrumentId: InstrumentId,
    val assetClass: AssetClass,
    val side: Side,
    val quantity: BigDecimal,
    val price: Money,
    val tradedAt: Instant,
)

data class BookTradeResult(
    val trade: Trade,
    val position: Position,
)

data class PortfolioSummary(
    val id: PortfolioId,
)

interface PositionServiceClient {
    suspend fun listPortfolios(): List<PortfolioSummary>
    suspend fun bookTrade(command: BookTradeCommand): BookTradeResult
    suspend fun getPositions(portfolioId: PortfolioId): List<Position>
    suspend fun getTradeHistory(portfolioId: PortfolioId): List<Trade>
}
