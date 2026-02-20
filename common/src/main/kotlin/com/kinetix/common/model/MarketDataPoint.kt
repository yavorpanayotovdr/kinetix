package com.kinetix.common.model

import java.math.BigDecimal
import java.time.Instant

data class MarketDataPoint(
    val instrumentId: InstrumentId,
    val price: Money,
    val timestamp: Instant,
    val source: MarketDataSource,
) {
    init {
        require(price.amount >= BigDecimal.ZERO) {
            "Market data price must be non-negative, was ${price.amount}"
        }
    }
}
