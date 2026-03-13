package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class RunManifestResponse(
    val manifestId: String,
    val jobId: String,
    val portfolioId: String,
    val valuationDate: String,
    val capturedAt: String,
    val modelVersion: String,
    val calculationType: String,
    val confidenceLevel: String,
    val timeHorizonDays: Int,
    val numSimulations: Int,
    val monteCarloSeed: Long,
    val positionCount: Int,
    val positionDigest: String,
    val marketDataDigest: String,
    val inputDigest: String,
    val status: String,
    val varValue: Double? = null,
    val expectedShortfall: Double? = null,
    val outputDigest: String? = null,
)
