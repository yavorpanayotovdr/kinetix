package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class RunComparisonResponse(
    val comparisonId: String,
    val comparisonType: String,
    val portfolioId: String,
    val baseRun: RunSnapshotResponse,
    val targetRun: RunSnapshotResponse,
    val portfolioDiff: PortfolioDiffResponse,
    val componentDiffs: List<ComponentDiffResponse>,
    val positionDiffs: List<PositionDiffResponse>,
    val parameterDiffs: List<ParameterDiffDto>,
    val attribution: VaRAttributionResponse?,
    val inputChanges: InputChangesSummaryDto? = null,
)
