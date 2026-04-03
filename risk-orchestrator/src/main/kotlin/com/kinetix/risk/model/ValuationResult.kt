package com.kinetix.risk.model

import com.kinetix.common.model.BookId
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ValuationResult(
    val bookId: BookId,
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
    val valuationDate: LocalDate? = null,
    val monteCarloSeed: Long = 0,
    val marketDataComplete: Boolean = true,
)
