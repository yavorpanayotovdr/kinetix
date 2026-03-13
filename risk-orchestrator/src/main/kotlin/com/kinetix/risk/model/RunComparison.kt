package com.kinetix.risk.model

import java.util.UUID

data class RunComparison(
    val comparisonId: UUID,
    val type: ComparisonType,
    val portfolioId: String,
    val baseRun: RunSnapshot,
    val targetRun: RunSnapshot,
    val portfolioDiff: PortfolioDiff,
    val componentDiffs: List<ComponentDiff>,
    val positionDiffs: List<PositionDiff>,
    val parameterDiffs: List<ParameterDiff>,
    val attribution: VaRAttribution?,
)
