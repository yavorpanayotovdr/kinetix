package com.kinetix.gateway.websocket

import com.kinetix.common.model.MarketDataPoint
import kotlinx.serialization.Serializable

@Serializable
data class ClientMessage(
    val type: String,
    val instrumentIds: List<String> = emptyList(),
)

@Serializable
data class PriceUpdate(
    val type: String = "price",
    val instrumentId: String,
    val priceAmount: String,
    val priceCurrency: String,
    val timestamp: String,
    val source: String,
) {
    companion object {
        fun from(point: MarketDataPoint): PriceUpdate = PriceUpdate(
            instrumentId = point.instrumentId.value,
            priceAmount = point.price.amount.toPlainString(),
            priceCurrency = point.price.currency.currencyCode,
            timestamp = point.timestamp.toString(),
            source = point.source.name,
        )
    }
}
