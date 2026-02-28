package com.kinetix.risk.service

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import java.math.BigDecimal

data class PositionPnlInput(
    val instrumentId: InstrumentId,
    val assetClass: AssetClass,
    val totalPnl: BigDecimal,
    val delta: BigDecimal,
    val gamma: BigDecimal,
    val vega: BigDecimal,
    val theta: BigDecimal,
    val rho: BigDecimal,
    val priceChange: BigDecimal,
    val volChange: BigDecimal,
    val rateChange: BigDecimal,
)
