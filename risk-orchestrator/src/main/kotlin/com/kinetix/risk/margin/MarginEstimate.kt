package com.kinetix.risk.margin

import java.math.BigDecimal
import java.util.Currency

data class MarginEstimate(
    val initialMargin: BigDecimal,
    val variationMargin: BigDecimal,
    val totalMargin: BigDecimal,
    val currency: Currency,
)
