package com.kinetix.risk.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class RunManifest(
    val manifestId: UUID,
    val jobId: UUID,
    val portfolioId: String,
    val valuationDate: LocalDate,
    val capturedAt: Instant,
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
    val status: ManifestStatus,
    val varValue: Double? = null,
    val expectedShortfall: Double? = null,
    val outputDigest: String? = null,
)
