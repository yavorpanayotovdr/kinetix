package com.kinetix.regulatory.stress.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateCorrelatedScenarioRequest(
    val name: String,
    val description: String,
    val primaryAssetClass: String,
    val primaryShock: Double,
    val assetClasses: List<String>,
    val createdBy: String,
)
