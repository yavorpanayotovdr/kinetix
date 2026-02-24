package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class GreeksResponse(
    val portfolioId: String,
    val assetClassGreeks: List<GreekValuesDto>,
    val theta: String,
    val rho: String,
    val calculatedAt: String,
)
