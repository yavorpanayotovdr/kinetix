package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class StressedCrossBookVaRResultResponse(
    val baseVaR: String,
    val stressedVaR: String,
    val baseDiversificationBenefit: String,
    val stressedDiversificationBenefit: String,
    val benefitErosion: String,
    val benefitErosionPct: String,
    val stressCorrelation: String,
)
