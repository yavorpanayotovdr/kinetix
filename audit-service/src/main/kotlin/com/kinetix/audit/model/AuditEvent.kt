package com.kinetix.audit.model

import java.time.Instant

data class AuditEvent(
    val id: Long = 0,
    val tradeId: String,
    val portfolioId: String,
    val instrumentId: String,
    val assetClass: String,
    val side: String,
    val quantity: String,
    val priceAmount: String,
    val priceCurrency: String,
    val tradedAt: String,
    val receivedAt: Instant,
)
