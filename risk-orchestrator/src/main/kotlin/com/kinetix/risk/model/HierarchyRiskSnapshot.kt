package com.kinetix.risk.model

import java.time.Instant

data class HierarchyRiskSnapshot(
    val snapshotAt: Instant,
    val level: HierarchyLevel,
    val entityId: String,
    val parentId: String?,
    val varValue: Double,
    val expectedShortfall: Double?,
    val pnlToday: Double?,
    val limitUtilisation: Double?,
    val marginalVar: Double?,
    val topContributors: List<RiskContributor>,
    val isPartial: Boolean = false,
)
