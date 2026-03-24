package com.kinetix.risk.model

data class RiskContributor(
    val entityId: String,
    val entityName: String,
    val varContribution: Double,
    val pctOfTotal: Double,
)
