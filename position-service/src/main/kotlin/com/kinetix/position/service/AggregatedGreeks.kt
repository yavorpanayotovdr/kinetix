package com.kinetix.position.service

import java.math.BigDecimal

data class AggregatedGreeks(
    val deltaNet: BigDecimal,
    val gammaNet: BigDecimal,
    val vegaNet: BigDecimal,
    val thetaNet: BigDecimal,
)
