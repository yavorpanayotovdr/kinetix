package com.kinetix.risk.model

data class HierarchyNodeRisk(
    val level: HierarchyLevel,
    val entityId: String,
    val entityName: String,
    val parentId: String?,
    val varValue: Double,
    val expectedShortfall: Double?,
    val pnlToday: Double?,
    val limitUtilisation: Double?,
    val marginalVar: Double?,
    val incrementalVar: Double?,
    val topContributors: List<RiskContributor>,
    val childCount: Int,
    val isPartial: Boolean = false,
    val missingBooks: List<String> = emptyList(),
)
