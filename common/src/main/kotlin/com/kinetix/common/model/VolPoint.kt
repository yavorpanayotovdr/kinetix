package com.kinetix.common.model

import java.math.BigDecimal

data class VolPoint(
    val strike: BigDecimal,
    val maturityDays: Int,
    val impliedVol: BigDecimal,
) {
    init {
        require(strike > BigDecimal.ZERO) { "Strike must be positive, was $strike" }
        require(maturityDays > 0) { "Maturity days must be positive, was $maturityDays" }
        require(impliedVol > BigDecimal.ZERO) { "Implied volatility must be positive, was $impliedVol" }
    }
}
