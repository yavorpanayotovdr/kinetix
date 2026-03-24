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
    val portfolioLvar: Double,
    val dataCompleteness: Double,
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
