package com.kinetix.price.kafka

import com.kinetix.common.model.PricePoint
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class PriceEvent(
    val instrumentId: String,
    val priceAmount: String,
    val priceCurrency: String,
    val timestamp: String,
    val source: String,
    val correlationId: String? = null,
) {
    companion object {
        fun from(point: PricePoint, correlationId: String? = null): PriceEvent = PriceEvent(
            instrumentId = point.instrumentId.value,
            priceAmount = point.price.amount.toPlainString(),
            priceCurrency = point.price.currency.currencyCode,
            timestamp = point.timestamp.toString(),
            source = point.source.name,
            correlationId = correlationId ?: UUID.randomUUID().toString(),
        )
    }
}
