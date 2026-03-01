package com.kinetix.position.model

import java.math.BigDecimal

data class LimitDefinition(
    val id: String,
    val level: LimitLevel,
    val entityId: String,
    val limitType: LimitType,
    val limitValue: BigDecimal,
    val intradayLimit: BigDecimal?,
    val overnightLimit: BigDecimal?,
    val active: Boolean,
)
