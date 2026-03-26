package com.kinetix.position.service

import com.kinetix.common.model.*
import com.kinetix.position.kafka.TradeEventPublisher
import com.kinetix.position.persistence.PositionRepository
import com.kinetix.position.persistence.TradeEventRepository
import org.slf4j.LoggerFactory

class TradeLifecycleService(
    private val tradeEventRepository: TradeEventRepository,
    private val positionRepository: PositionRepository,
    private val transactional: TransactionalRunner,
    private val tradeEventPublisher: TradeEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(TradeLifecycleService::class.java)

    suspend fun handleAmend(command: AmendTradeCommand): BookTradeResult {
        logger.info("Amending trade: originalTradeId={}, newTradeId={}, book={}",
            command.originalTradeId.value, command.newTradeId.value, command.bookId.value)

        val originalTrade = tradeEventRepository.findByTradeId(command.originalTradeId)
            ?: throw IllegalArgumentException("Trade not found: ${command.originalTradeId.value}")
        if (originalTrade.status != TradeStatus.LIVE) {
            throw InvalidTradeStateException(command.originalTradeId.value, originalTrade.status, "amend")
        }

        val result = transactional.run {
            tradeEventRepository.updateStatus(command.originalTradeId, TradeStatus.AMENDED)

            val currentPosition = positionRepository.findByKey(originalTrade.bookId, originalTrade.instrumentId)
                ?: Position.empty(originalTrade.bookId, originalTrade.instrumentId, originalTrade.assetClass, originalTrade.price.currency)

            val reverseTrade = createReverseTrade(originalTrade)
            val positionAfterReversal = currentPosition.applyTrade(reverseTrade)

            val amendTrade = Trade(
                tradeId = command.newTradeId,
                bookId = command.bookId,
                instrumentId = command.instrumentId,
                assetClass = command.assetClass,
                side = command.side,
                quantity = command.quantity,
                price = command.price,
                tradedAt = command.tradedAt,
                eventType = TradeEventType.AMEND,
                status = TradeStatus.LIVE,
                originalTradeId = command.originalTradeId,
                counterpartyId = command.counterpartyId,
            )

            val finalPosition = positionAfterReversal.applyTrade(amendTrade)

            tradeEventRepository.save(amendTrade)
            positionRepository.save(finalPosition)

            BookTradeResult(amendTrade, finalPosition)
        }

        tradeEventPublisher.publish(TradeEvent(trade = result.trade, userId = command.userId, userRole = command.userRole))
        logger.info("Trade amended: originalTradeId={}, newTradeId={}", command.originalTradeId.value, command.newTradeId.value)
        return result
    }

    suspend fun handleCancel(command: CancelTradeCommand): BookTradeResult {
        logger.info("Cancelling trade: tradeId={}", command.tradeId.value)

        val trade = tradeEventRepository.findByTradeId(command.tradeId)
            ?: throw IllegalArgumentException("Trade not found: ${command.tradeId.value}")
        if (trade.status != TradeStatus.LIVE) {
            throw InvalidTradeStateException(command.tradeId.value, trade.status, "cancel")
        }

        val result = transactional.run {
            tradeEventRepository.updateStatus(command.tradeId, TradeStatus.CANCELLED)

            val currentPosition = positionRepository.findByKey(trade.bookId, trade.instrumentId)
                ?: Position.empty(trade.bookId, trade.instrumentId, trade.assetClass, trade.price.currency)

            val reverseTrade = createReverseTrade(trade)
            val updatedPosition = currentPosition.applyTrade(reverseTrade)

            positionRepository.save(updatedPosition)

            val cancelledTrade = trade.copy(status = TradeStatus.CANCELLED)
            BookTradeResult(cancelledTrade, updatedPosition)
        }

        tradeEventPublisher.publish(TradeEvent(trade = result.trade))
        logger.info("Trade cancelled: tradeId={}", command.tradeId.value)
        return result
    }

    private fun createReverseTrade(trade: Trade): Trade {
        val reverseSide = when (trade.side) {
            Side.BUY -> Side.SELL
            Side.SELL -> Side.BUY
        }
        return Trade(
            tradeId = TradeId("${trade.tradeId.value}-reverse"),
            bookId = trade.bookId,
            instrumentId = trade.instrumentId,
            assetClass = trade.assetClass,
            side = reverseSide,
            quantity = trade.quantity,
            price = trade.price,
            tradedAt = trade.tradedAt,
            eventType = trade.eventType,
            status = trade.status,
        )
    }
}
