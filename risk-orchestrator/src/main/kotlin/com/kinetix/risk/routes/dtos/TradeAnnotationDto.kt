package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class TradeAnnotationDto(
    val timestamp: String,
    val instrumentId: String,
    val side: String,
    val quantity: String,
    val tradeId: String,
)
