package com.kinetix.risk.model

import java.time.Instant

data class FactorDecompositionSnapshot(
    val bookId: String,
    val calculatedAt: Instant,
    val totalVar: Double,
    val systematicVar: Double,
    val idiosyncraticVar: Double,
    val rSquared: Double,
    val concentrationWarning: Boolean,
    val factors: List<FactorContribution>,
)
