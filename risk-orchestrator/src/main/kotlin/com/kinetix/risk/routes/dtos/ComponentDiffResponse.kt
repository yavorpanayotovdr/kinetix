package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class ComponentDiffResponse(
    val assetClass: String,
    val baseContribution: String,
    val targetContribution: String,
    val change: String,
    val changePercent: String?,
)
