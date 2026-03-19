package com.kinetix.common.kafka.events

import com.kinetix.common.model.TradeEvent
import kotlinx.serialization.Serializable

/**
 * Kafka serialization adapter for trade lifecycle messages on the `trades.lifecycle` topic.
 *
 * This is the wire format — the domain model is [TradeEvent].
 */
@Serializable
data class TradeEventMessage(
    val tradeId: String,
    val portfolioId: String,
    val instrumentId: String,
    val assetClass: String,
    val side: String,
    val quantity: String,
    val priceAmount: String,
    val priceCurrency: String,
    val tradedAt: String,
    val eventType: String = "NEW",
    val status: String = "LIVE",
    val originalTradeId: String? = null,
    val correlationId: String? = null,
    val userId: String? = null,
    val userRole: String? = null,
    val auditEventType: String = "TRADE_BOOKED",
    val bookId: String = portfolioId,
    val instrumentType: String? = null,
) {
    companion object {
        fun from(event: TradeEvent): TradeEventMessage = TradeEventMessage(
            tradeId = event.trade.tradeId.value,
            portfolioId = event.trade.bookId.value,
            instrumentId = event.trade.instrumentId.value,
            assetClass = event.trade.assetClass.name,
            side = event.trade.side.name,
            quantity = event.trade.quantity.toPlainString(),
            priceAmount = event.trade.price.amount.toPlainString(),
            priceCurrency = event.trade.price.currency.currencyCode,
            tradedAt = event.trade.tradedAt.toString(),
            eventType = event.trade.eventType.name,
            status = event.trade.status.name,
            originalTradeId = event.trade.originalTradeId?.value,
            correlationId = event.correlationId,
            userId = event.userId,
            userRole = event.userRole,
            auditEventType = event.auditEventType,
            bookId = event.trade.bookId.value,
        )
    }
}
