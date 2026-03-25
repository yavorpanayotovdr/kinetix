package com.kinetix.risk.client.dtos

import com.kinetix.risk.model.TradeAnnotation
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class TradeDto(
    val tradeId: String,
    val bookId: String,
    val instrumentId: String,
    val assetClass: String,
    val side: String,
    val quantity: String,
    val tradedAt: String,
) {
    fun toTradeAnnotation(): TradeAnnotation = TradeAnnotation(
        timestamp = Instant.parse(tradedAt),
        instrumentId = instrumentId,
        side = side,
        quantity = quantity,
        tradeId = tradeId,
    )
}
