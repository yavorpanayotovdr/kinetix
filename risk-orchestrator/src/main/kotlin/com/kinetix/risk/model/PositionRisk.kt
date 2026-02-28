package com.kinetix.risk.model

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import java.math.BigDecimal

data class PositionRisk(
    val instrumentId: InstrumentId,
    val assetClass: AssetClass,
    val marketValue: BigDecimal,
    val delta: Double?,
    val gamma: Double?,
    val vega: Double?,
    val varContribution: BigDecimal,
    val esContribution: BigDecimal,
    val percentageOfTotal: BigDecimal,
)
