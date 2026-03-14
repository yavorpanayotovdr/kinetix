package com.kinetix.risk.model

data class VaRAttribution(
    val totalChange: Double,
    val positionEffect: Double,
    val volEffect: Double?,
    val corrEffect: Double?,
    val modelEffect: Double?,
    val timeDecayEffect: Double,
    val unexplained: Double,
    val effectMagnitudes: Map<String, ChangeMagnitude> = emptyMap(),
    val caveats: List<String> = emptyList(),
)
