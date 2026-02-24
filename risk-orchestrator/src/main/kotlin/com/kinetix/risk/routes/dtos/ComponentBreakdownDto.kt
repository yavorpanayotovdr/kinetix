package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class ComponentBreakdownDto(
    val assetClass: String,
    val varContribution: String,
    val percentageOfTotal: String,
)
