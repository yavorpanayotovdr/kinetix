package com.kinetix.position.service

import java.math.BigDecimal

data class LegGreeks(
    val delta: BigDecimal,
    val gamma: BigDecimal,
    val vega: BigDecimal,
    val theta: BigDecimal,
)
