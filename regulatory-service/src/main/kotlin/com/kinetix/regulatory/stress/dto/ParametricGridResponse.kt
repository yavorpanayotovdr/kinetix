package com.kinetix.regulatory.stress.dto

import kotlinx.serialization.Serializable

@Serializable
data class GridCellResponse(
    val primaryAxis: String,
    val primaryShock: Double,
    val secondaryAxis: String,
    val secondaryShock: Double,
    val pnlImpact: String,
)

@Serializable
data class ParametricGridResponse(
    val primaryAxis: String,
    val secondaryAxis: String,
    val cells: List<GridCellResponse>,
    val worstPnlImpact: String?,
)
