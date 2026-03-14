package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class InputChangesSummaryDto(
    val positionsChanged: Boolean,
    val marketDataChanged: Boolean,
    val modelVersionChanged: Boolean,
    val baseModelVersion: String,
    val targetModelVersion: String,
    val positionChanges: List<PositionInputChangeDto>,
    val marketDataChanges: List<MarketDataInputChangeDto>,
)
