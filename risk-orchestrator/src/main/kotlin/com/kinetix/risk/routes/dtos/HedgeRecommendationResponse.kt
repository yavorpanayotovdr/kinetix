package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class HedgeRecommendationResponse(
    val id: String,
    val bookId: String,
    val targetMetric: String,
    val targetReductionPct: Double,
    val requestedAt: String,
    val status: String,
    val message: String? = null,
    val expiresAt: String,
    val acceptedBy: String?,
    val acceptedAt: String?,
    val sourceJobId: String?,
    val suggestions: List<HedgeSuggestionDto>,
    val preHedgeGreeks: GreekImpactDto,
    val totalEstimatedCost: Double,
    val isExpired: Boolean,
)

@Serializable
data class HedgeSuggestionDto(
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
    val greekImpact: GreekImpactDto,
    val liquidityTier: String,
    val dataQuality: String,
    val warnings: List<String> = emptyList(),
)

@Serializable
data class GreekImpactDto(
    val deltaBefore: Double,
    val deltaAfter: Double,
    val gammaBefore: Double,
    val gammaAfter: Double,
    val vegaBefore: Double,
    val vegaAfter: Double,
    val thetaBefore: Double,
    val thetaAfter: Double,
    val rhoBefore: Double,
    val rhoAfter: Double,
)
