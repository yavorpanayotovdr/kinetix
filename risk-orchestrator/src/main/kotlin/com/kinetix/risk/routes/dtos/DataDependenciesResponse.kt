package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class DataDependenciesResponse(
    val portfolioId: String,
    val dependencies: List<MarketDataDependencyDto>,
)
