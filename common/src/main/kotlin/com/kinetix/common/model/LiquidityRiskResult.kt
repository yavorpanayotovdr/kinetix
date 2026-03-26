package com.kinetix.common.model

/**
 * Domain model representing the liquidity risk assessment for a book.
 *
 * This is the primary risk metric produced by the liquidity risk pipeline.
 * Instrument liquidity data (ADV, spread) is reference data; position
 * liquidity is the derived risk metric.
 */
data class LiquidityRiskResult(
    val bookId: String,
    val var1day: Double = 0.0,
    val portfolioLvar: Double,
    val lvarRatio: Double = 0.0,
    val weightedAvgHorizon: Double = 0.0,
    val maxHorizon: Double = 0.0,
    val concentrationCount: Int = 0,
    val dataCompleteness: Double,
    val advDataAsOf: String? = null,
    val positionRisks: List<PositionLiquidityRisk>,
    val portfolioConcentrationStatus: String,
    val calculatedAt: String,
)

data class PositionLiquidityRisk(
    val instrumentId: String,
    val assetClass: String,
    val marketValue: Double,
    val tier: LiquidityTier,
    val horizonDays: Int,
    val adv: Double?,
    val advPct: Double? = null,
    val advMissing: Boolean,
    val advStale: Boolean,
    val lvarContribution: Double,
    val stressedLiquidationValue: Double,
    val concentrationStatus: String,
)

enum class LiquidityTier {
    HIGH_LIQUID,
    LIQUID,
    SEMI_LIQUID,
    ILLIQUID,
}
