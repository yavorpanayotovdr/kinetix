package com.kinetix.common.model

import java.math.BigDecimal
import java.time.Instant

data class PricePoint(
    val instrumentId: InstrumentId,
    val price: Money,
    val timestamp: Instant,
    val source: PriceSource,
) {
    init {
        require(price.amount >= BigDecimal.ZERO) {
            "Price must be non-negative, was ${price.amount}"
        }
    }
}
