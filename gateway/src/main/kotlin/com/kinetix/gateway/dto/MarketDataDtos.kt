package com.kinetix.gateway.dto

import com.kinetix.common.model.MarketDataPoint
import kotlinx.serialization.Serializable

@Serializable
data class MarketDataPointResponse(
    val instrumentId: String,
    val price: MoneyDto,
    val timestamp: String,
    val source: String,
)

fun MarketDataPoint.toResponse(): MarketDataPointResponse = MarketDataPointResponse(
    instrumentId = instrumentId.value,
    price = price.toDto(),
    timestamp = timestamp.toString(),
    source = source.name,
)
