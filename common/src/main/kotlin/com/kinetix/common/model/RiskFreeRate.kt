package com.kinetix.common.model

import java.time.Instant
import java.util.Currency

data class RiskFreeRate(
    val currency: Currency,
    val tenor: String,
    val rate: Double,
    val asOfDate: Instant,
    val source: RateSource,
) {
    init {
        require(tenor.isNotBlank()) { "Tenor must not be blank" }
    }
}
