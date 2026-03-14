package com.kinetix.risk.model

data class QuantDiffResult(
    val magnitude: ChangeMagnitude,
    val summary: String? = null,
    val caveats: List<String> = emptyList(),
)
