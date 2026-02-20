package com.kinetix.position.service

import com.kinetix.common.model.*
import com.kinetix.position.persistence.PositionRepository
import com.kinetix.position.persistence.TradeEventRepository
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

class TradeBookingService(
    private val tradeEventRepository: TradeEventRepository,
    private val positionRepository: PositionRepository,
    private val transactional: TransactionalRunner,
) {
    suspend fun handle(command: BookTradeCommand): BookTradeResult {
        val trade = Trade(
            tradeId = command.tradeId,
            portfolioId = command.portfolioId,
            instrumentId = command.instrumentId,
            assetClass = command.assetClass,
            side = command.side,
            quantity = command.quantity,
            price = command.price,
            tradedAt = command.tradedAt,
        )

        return transactional.run {
            val existing = tradeEventRepository.findByTradeId(trade.tradeId)
            if (existing != null) {
                val position = positionRepository.findByKey(trade.portfolioId, trade.instrumentId)
                    ?: Position.empty(trade.portfolioId, trade.instrumentId, trade.assetClass, trade.price.currency)
                return@run BookTradeResult(existing, position)
            }

            tradeEventRepository.save(trade)

            val currentPosition = positionRepository.findByKey(trade.portfolioId, trade.instrumentId)
                ?: Position.empty(trade.portfolioId, trade.instrumentId, trade.assetClass, trade.price.currency)

            val updatedPosition = currentPosition.applyTrade(trade)
            positionRepository.save(updatedPosition)

            BookTradeResult(trade, updatedPosition)
        }
    }
}
