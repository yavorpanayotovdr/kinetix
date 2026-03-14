package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class PositionInputChangeDto(
    val instrumentId: String,
    val assetClass: String,
    val changeType: String,
    val baseQuantity: String?,
    val targetQuantity: String?,
    val quantityDelta: String?,
    val baseMarketPrice: String?,
    val targetMarketPrice: String?,
    val priceDelta: String?,
    val currency: String,
)
