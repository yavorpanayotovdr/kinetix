package com.kinetix.position.kafka

import com.kinetix.common.model.Trade
import kotlinx.serialization.Serializable
import java.util.UUID

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
