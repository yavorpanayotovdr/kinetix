package com.kinetix.position.kafka

import kotlinx.serialization.Serializable

@Serializable
data class PriceEvent(
    val instrumentId: String,
    val priceAmount: String,
    val priceCurrency: String,
    val timestamp: String,
    val source: String,
    val correlationId: String? = null,
)
