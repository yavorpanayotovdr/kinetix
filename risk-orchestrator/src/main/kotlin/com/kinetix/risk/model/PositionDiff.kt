package com.kinetix.risk.model

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import java.math.BigDecimal

data class PositionDiff(
    val instrumentId: InstrumentId,
    val assetClass: AssetClass,
    val changeType: PositionChangeType,
    val baseMarketValue: BigDecimal,
    val targetMarketValue: BigDecimal,
    val marketValueChange: BigDecimal,
    val baseVarContribution: BigDecimal,
    val targetVarContribution: BigDecimal,
    val varContributionChange: BigDecimal,
    val baseDelta: Double?,
    val targetDelta: Double?,
    val baseGamma: Double?,
    val targetGamma: Double?,
    val baseVega: Double?,
    val targetVega: Double?,
)
