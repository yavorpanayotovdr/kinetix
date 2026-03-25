package com.kinetix.position.service

import com.kinetix.common.model.*
import com.kinetix.position.kafka.TradeEventPublisher
import com.kinetix.position.model.LimitBreach
import com.kinetix.position.persistence.PositionRepository
import com.kinetix.position.persistence.TradeEventRepository
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant

data class BookTradeCommand(
    val tradeId: TradeId,
    val bookId: BookId,
    val instrumentId: InstrumentId,
    val assetClass: AssetClass,
    val side: Side,
    val quantity: BigDecimal,
    val price: Money,
    val tradedAt: Instant,
    val instrumentType: String? = null,
    val userId: String? = null,
    val userRole: String? = null,
    val strategyId: String? = null,
)

data class BookTradeResult(
    val trade: Trade,
    val position: Position,
    val warnings: List<LimitBreach> = emptyList(),
)

class TradeBookingService(
    private val tradeEventRepository: TradeEventRepository,
    private val positionRepository: PositionRepository,
    private val transactional: TransactionalRunner,
    private val tradeEventPublisher: TradeEventPublisher,
    private val limitCheckService: PreTradeCheckService? = null,
) {
    private val logger = LoggerFactory.getLogger(TradeBookingService::class.java)

    suspend fun handle(command: BookTradeCommand): BookTradeResult {
        logger.info("Booking trade: tradeId={}, book={}, instrument={}, side={}, qty={}, price={}",
            command.tradeId.value, command.bookId.value, command.instrumentId.value,
            command.side, command.quantity, command.price.amount)
        val limitResult = limitCheckService?.check(command)
        if (limitResult != null && limitResult.blocked) {
            throw LimitBreachException(limitResult)
        }
        val warnings = limitResult?.breaches ?: emptyList()

        val trade = Trade(
            tradeId = command.tradeId,
            bookId = command.bookId,
            instrumentId = command.instrumentId,
            assetClass = command.assetClass,
            side = command.side,
            quantity = command.quantity,
            price = command.price,
            tradedAt = command.tradedAt,
            instrumentType = command.instrumentType,
            strategyId = command.strategyId,
        )

        val (result, isNewTrade) = transactional.run {
            val existing = tradeEventRepository.findByTradeId(trade.tradeId)
            if (existing != null) {
                val position = positionRepository.findByKey(trade.bookId, trade.instrumentId)
                    ?: Position.empty(trade.bookId, trade.instrumentId, trade.assetClass, trade.price.currency)
                return@run Pair(BookTradeResult(existing, position, warnings), false)
            }

            tradeEventRepository.save(trade)

            val currentPosition = positionRepository.findByKey(trade.bookId, trade.instrumentId)
                ?: Position.empty(trade.bookId, trade.instrumentId, trade.assetClass, trade.price.currency)

            val updatedPosition = currentPosition.applyTrade(trade)
                .let { pos -> if (trade.strategyId != null) pos.copy(strategyId = trade.strategyId) else pos }
            positionRepository.save(updatedPosition)

            Pair(BookTradeResult(trade, updatedPosition, warnings), true)
        }

        if (isNewTrade) {
            tradeEventPublisher.publish(TradeEvent(trade = result.trade, userId = command.userId, userRole = command.userRole))
            logger.info("Trade booked: tradeId={}, book={}, newPosition={}",
                result.trade.tradeId.value, result.trade.bookId.value, result.position.quantity)
        }

        return result
    }
}
