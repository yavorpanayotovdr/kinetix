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
        logger.info("Amending trade: originalTradeId={}, newTradeId={}, portfolio={}",
            command.originalTradeId.value, command.newTradeId.value, command.portfolioId.value)
        val result = transactional.run {
            val originalTrade = tradeEventRepository.findByTradeId(command.originalTradeId)
                ?: throw IllegalArgumentException("Trade not found: ${command.originalTradeId.value}")

            check(originalTrade.status == TradeStatus.LIVE) {
                "Cannot amend trade in status ${originalTrade.status}"
            }

            tradeEventRepository.updateStatus(command.originalTradeId, TradeStatus.AMENDED)

            val currentPosition = positionRepository.findByKey(originalTrade.portfolioId, originalTrade.instrumentId)
                ?: Position.empty(originalTrade.portfolioId, originalTrade.instrumentId, originalTrade.assetClass, originalTrade.price.currency)

            val reverseTrade = createReverseTrade(originalTrade)
            val positionAfterReversal = currentPosition.applyTrade(reverseTrade)

            val amendTrade = Trade(
                tradeId = command.newTradeId,
                portfolioId = command.portfolioId,
                instrumentId = command.instrumentId,
                assetClass = command.assetClass,
                side = command.side,
                quantity = command.quantity,
                price = command.price,
                tradedAt = command.tradedAt,
                type = TradeType.AMEND,
                status = TradeStatus.LIVE,
                originalTradeId = command.originalTradeId,
            )

            val finalPosition = positionAfterReversal.applyTrade(amendTrade)

            tradeEventRepository.save(amendTrade)
            positionRepository.save(finalPosition)

            BookTradeResult(amendTrade, finalPosition)
        }

        tradeEventPublisher.publish(result.trade)
        logger.info("Trade amended: originalTradeId={}, newTradeId={}", command.originalTradeId.value, command.newTradeId.value)
        return result
    }

    suspend fun handleCancel(command: CancelTradeCommand): BookTradeResult {
        logger.info("Cancelling trade: tradeId={}", command.tradeId.value)
        val result = transactional.run {
            val trade = tradeEventRepository.findByTradeId(command.tradeId)
                ?: throw IllegalArgumentException("Trade not found: ${command.tradeId.value}")

            check(trade.status == TradeStatus.LIVE) {
                "Cannot cancel trade in status ${trade.status}"
            }

            tradeEventRepository.updateStatus(command.tradeId, TradeStatus.CANCELLED)

            val currentPosition = positionRepository.findByKey(trade.portfolioId, trade.instrumentId)
                ?: Position.empty(trade.portfolioId, trade.instrumentId, trade.assetClass, trade.price.currency)

            val reverseTrade = createReverseTrade(trade)
            val updatedPosition = currentPosition.applyTrade(reverseTrade)

            positionRepository.save(updatedPosition)

            val cancelledTrade = trade.copy(status = TradeStatus.CANCELLED)
            BookTradeResult(cancelledTrade, updatedPosition)
        }

        tradeEventPublisher.publish(result.trade)
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
            portfolioId = trade.portfolioId,
            instrumentId = trade.instrumentId,
            assetClass = trade.assetClass,
            side = reverseSide,
            quantity = trade.quantity,
            price = trade.price,
            tradedAt = trade.tradedAt,
            type = trade.type,
            status = trade.status,
        )
    }
}
