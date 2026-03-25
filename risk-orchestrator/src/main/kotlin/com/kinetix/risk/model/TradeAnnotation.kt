package com.kinetix.risk.model

import java.time.Instant

data class TradeAnnotation(
    val timestamp: Instant,
    val instrumentId: String,
    val side: String,
    val quantity: String,
    val tradeId: String,
)
