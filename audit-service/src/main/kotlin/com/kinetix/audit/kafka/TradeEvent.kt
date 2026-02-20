package com.kinetix.audit.kafka

import com.kinetix.audit.model.AuditEvent
import kotlinx.serialization.Serializable
import java.time.Instant

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
) {
    fun toAuditEvent(receivedAt: Instant = Instant.now()): AuditEvent = AuditEvent(
        tradeId = tradeId,
        portfolioId = portfolioId,
        instrumentId = instrumentId,
        assetClass = assetClass,
        side = side,
        quantity = quantity,
        priceAmount = priceAmount,
        priceCurrency = priceCurrency,
        tradedAt = tradedAt,
        receivedAt = receivedAt,
    )
}
