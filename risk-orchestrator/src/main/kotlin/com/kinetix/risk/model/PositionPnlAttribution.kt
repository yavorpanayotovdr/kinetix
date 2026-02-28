package com.kinetix.risk.model

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import java.math.BigDecimal

data class PositionPnlAttribution(
    val instrumentId: InstrumentId,
    val assetClass: AssetClass,
    val totalPnl: BigDecimal,
    val deltaPnl: BigDecimal,
    val gammaPnl: BigDecimal,
    val vegaPnl: BigDecimal,
    val thetaPnl: BigDecimal,
    val rhoPnl: BigDecimal,
    val unexplainedPnl: BigDecimal,
)
