package com.kinetix.common.model

import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

data class FxRate(
    val baseCurrency: Currency,
    val quoteCurrency: Currency,
    val rate: BigDecimal,
    val asOf: Instant,
)
