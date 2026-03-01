package com.kinetix.position.model

import com.kinetix.common.model.Money
import java.math.BigDecimal

data class TradeLimits(
    val positionLimit: BigDecimal? = null,
    val notionalLimit: Money? = null,
    val concentrationLimitPct: Double? = null,
    val softLimitPct: Double = 0.8,
)
