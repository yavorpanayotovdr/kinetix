package com.kinetix.regulatory.stress

data class GridCell(
    val primaryAxis: String,
    val primaryShock: Double,
    val secondaryAxis: String,
    val secondaryShock: Double,
    val pnlImpact: String,
)

data class ParametricGridResult(
    val primaryAxis: String,
    val secondaryAxis: String,
    val cells: List<GridCell>,
    val worstPnlImpact: String?,
)
