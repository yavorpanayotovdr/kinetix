package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class VaRResultResponse(
    val bookId: String,
    val calculationType: String,
    val confidenceLevel: String,
    val varValue: String,
    val expectedShortfall: String,
    val componentBreakdown: List<ComponentBreakdownDto>,
    val calculatedAt: String,
    val greeks: GreeksResponse? = null,
    val computedOutputs: List<String>? = null,
    val pvValue: String? = null,
    val positionRisk: List<PositionRiskDto>? = null,
    val valuationDate: String? = null,
    val marketDataComplete: Boolean = true,
    val stale: Boolean? = null,
)
