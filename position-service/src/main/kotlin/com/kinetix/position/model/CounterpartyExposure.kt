package com.kinetix.position.model

import java.math.BigDecimal

data class CounterpartyExposure(
    val counterpartyId: String,
    val netExposure: BigDecimal,
    val grossExposure: BigDecimal,
    val positionCount: Int,
)
