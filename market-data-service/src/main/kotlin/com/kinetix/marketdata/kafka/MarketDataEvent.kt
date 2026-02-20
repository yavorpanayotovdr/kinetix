package com.kinetix.marketdata.kafka

import com.kinetix.common.model.MarketDataPoint
import kotlinx.serialization.Serializable

@Serializable
data class MarketDataEvent(
    val instrumentId: String,
    val priceAmount: String,
    val priceCurrency: String,
    val timestamp: String,
    val source: String,
) {
    companion object {
        fun from(point: MarketDataPoint): MarketDataEvent = MarketDataEvent(
            instrumentId = point.instrumentId.value,
            priceAmount = point.price.amount.toPlainString(),
            priceCurrency = point.price.currency.currencyCode,
            timestamp = point.timestamp.toString(),
            source = point.source.name,
        )
    }
}
