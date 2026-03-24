package com.kinetix.position.fix

import java.math.BigDecimal

data class ReconciliationBreak(
    val instrumentId: String,
    val internalQty: BigDecimal,
    val primeBrokerQty: BigDecimal,
    val breakQty: BigDecimal,
    val breakNotional: BigDecimal,
)
