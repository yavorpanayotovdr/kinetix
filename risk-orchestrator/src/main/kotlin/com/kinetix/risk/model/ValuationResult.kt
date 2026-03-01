package com.kinetix.risk.model

import com.kinetix.common.model.PortfolioId
import java.time.Instant
import java.util.UUID

data class ValuationResult(
    val portfolioId: PortfolioId,
    val calculationType: CalculationType,
    val confidenceLevel: ConfidenceLevel,
    val varValue: Double?,
    val expectedShortfall: Double?,
    val componentBreakdown: List<ComponentBreakdown>,
    val greeks: GreeksResult?,
    val calculatedAt: Instant,
    val computedOutputs: Set<ValuationOutput>,
    val pvValue: Double? = null,
    val positionRisk: List<PositionRisk> = emptyList(),
    val jobId: UUID? = null,
    val modelVersion: String? = null,
)
