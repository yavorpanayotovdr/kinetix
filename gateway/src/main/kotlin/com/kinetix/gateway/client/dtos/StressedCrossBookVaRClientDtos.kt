package com.kinetix.gateway.client.dtos

import com.kinetix.gateway.client.StressedCrossBookVaRResultSummary
import kotlinx.serialization.Serializable

@Serializable
data class StressedCrossBookVaRRequestClientDto(
    val bookIds: List<String>,
    val portfolioGroupId: String,
    val stressCorrelation: Double? = null,
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
    val timeHorizonDays: String? = null,
    val numSimulations: String? = null,
)

@Serializable
data class StressedCrossBookVaRResultClientDto(
    val baseVaR: String,
    val stressedVaR: String,
    val baseDiversificationBenefit: String,
    val stressedDiversificationBenefit: String,
    val benefitErosion: String,
    val benefitErosionPct: String,
    val stressCorrelation: String,
)

fun StressedCrossBookVaRResultClientDto.toDomain() = StressedCrossBookVaRResultSummary(
    baseVaR = baseVaR,
    stressedVaR = stressedVaR,
    baseDiversificationBenefit = baseDiversificationBenefit,
    stressedDiversificationBenefit = stressedDiversificationBenefit,
    benefitErosion = benefitErosion,
    benefitErosionPct = benefitErosionPct,
    stressCorrelation = stressCorrelation,
)
