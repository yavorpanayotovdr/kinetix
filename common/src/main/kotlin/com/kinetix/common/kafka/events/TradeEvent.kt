package com.kinetix.common.kafka.events

import com.kinetix.common.model.Trade
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Canonical Kafka event schema for trade lifecycle messages on the `trades.lifecycle` topic.
 *
 * This is the superset of all per-service TradeEvent definitions. Fields added by the
 * audit-service (userId, userRole, eventType) are nullable/defaulted for backward compatibility.
 */
@Serializable
data class TradeEvent(
    val tradeId: String,
    val portfolioId: String,
    val instrumentId: String,
    val assetClass: String,
    val side: String,
    val quantity: String,
    val priceAmount: String,
    val priceCurrency: String,
    val tradedAt: String,
    val type: String = "NEW",
    val status: String = "LIVE",
    val originalTradeId: String? = null,
    val correlationId: String? = null,
    val userId: String? = null,
    val userRole: String? = null,
    val eventType: String = "TRADE_BOOKED",
) {
    companion object {
        fun from(trade: Trade, correlationId: String? = null): TradeEvent = TradeEvent(
            tradeId = trade.tradeId.value,
            portfolioId = trade.portfolioId.value,
            instrumentId = trade.instrumentId.value,
            assetClass = trade.assetClass.name,
            side = trade.side.name,
            quantity = trade.quantity.toPlainString(),
            priceAmount = trade.price.amount.toPlainString(),
            priceCurrency = trade.price.currency.currencyCode,
            tradedAt = trade.tradedAt.toString(),
            type = trade.type.name,
            status = trade.status.name,
            originalTradeId = trade.originalTradeId?.value,
            correlationId = correlationId ?: UUID.randomUUID().toString(),
        )
    }
}
