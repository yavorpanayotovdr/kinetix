package com.kinetix.risk.model

data class HedgeSuggestion(
    val instrumentId: String,
    val instrumentType: String,
    val side: String,
    val quantity: Double,
    val estimatedCost: Double,
    val crossingCost: Double,
    val carrycostPerDay: Double?,
    val targetReduction: Double,
    val targetReductionPct: Double,
    val residualMetric: Double,
    val greekImpact: GreekImpact,
    val liquidityTier: String,
    val dataQuality: String,
    val warnings: List<String> = emptyList(),
)
