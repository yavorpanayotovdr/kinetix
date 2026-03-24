package com.kinetix.position.fix

import java.math.BigDecimal

/**
 * A single position entry from a prime broker daily statement.
 */
data class PrimeBrokerPosition(
    val instrumentId: String,
    val quantity: BigDecimal,
    val price: BigDecimal,
)
