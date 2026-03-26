package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class HierarchyNodeRiskResponse(
    val level: String,
    val entityId: String,
    val entityName: String,
    val parentId: String?,
    val varValue: String,
    val expectedShortfall: String?,
    val pnlToday: String?,
    val limitUtilisation: String?,
    val marginalVar: String?,
    val incrementalVar: String?,
    val topContributors: List<RiskContributorResponse>,
    val childCount: Int,
    val isPartial: Boolean,
    val missingBooks: List<String>,
    /** ISO-8601 timestamp at which this report was generated. Used by consumers to assess data freshness. */
    val generatedAt: String,
)
