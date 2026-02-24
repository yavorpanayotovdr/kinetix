package com.kinetix.risk.model

data class DiscoveredDependency(
    val dataType: String,
    val instrumentId: String,
    val assetClass: String,
    val parameters: Map<String, String> = emptyMap(),
)
