package com.kinetix.risk.kafka

import kotlinx.serialization.Serializable

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
    val correlationId: String? = null,
)
