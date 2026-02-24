package com.kinetix.risk.kafka

import kotlinx.serialization.Serializable

@Serializable
data class PriceEvent(
    val instrumentId: String,
    val priceAmount: String,
    val priceCurrency: String,
    val timestamp: String,
    val source: String,
)
