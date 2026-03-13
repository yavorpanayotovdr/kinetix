package com.kinetix.risk.model

import com.kinetix.common.model.AssetClass

data class ComponentDiff(
    val assetClass: AssetClass,
    val baseContribution: Double,
    val targetContribution: Double,
    val change: Double,
    val changePercent: Double?,
)
