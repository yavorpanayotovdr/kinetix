package com.kinetix.regulatory.stress.dto

import kotlinx.serialization.Serializable

@Serializable
data class StressTestResultResponse(
    val id: String,
    val scenarioId: String,
    val bookId: String,
    val calculatedAt: String,
    val basePv: String?,
    val stressedPv: String?,
    val pnlImpact: String?,
    val varImpact: String?,
    val positionImpacts: String?,
    val modelVersion: String?,
)
