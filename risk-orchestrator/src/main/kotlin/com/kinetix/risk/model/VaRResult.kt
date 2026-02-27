package com.kinetix.risk.model

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.PortfolioId
import java.time.Instant

enum class CalculationType {
    HISTORICAL,
    PARAMETRIC,
    MONTE_CARLO,
}

enum class ConfidenceLevel(val value: Double) {
    CL_95(0.95),
    CL_99(0.99),
}

data class ComponentBreakdown(
    val assetClass: AssetClass,
    val varContribution: Double,
    val percentageOfTotal: Double,
)

data class VaRResult(
    val portfolioId: PortfolioId,
    val calculationType: CalculationType,
    val confidenceLevel: ConfidenceLevel,
    val varValue: Double,
    val expectedShortfall: Double,
    val componentBreakdown: List<ComponentBreakdown>,
    val calculatedAt: Instant,
)

data class VaRCalculationRequest(
    val portfolioId: PortfolioId,
    val calculationType: CalculationType,
    val confidenceLevel: ConfidenceLevel,
    val timeHorizonDays: Int = 1,
    val numSimulations: Int = 10_000,
    val requestedOutputs: Set<ValuationOutput> = setOf(ValuationOutput.VAR, ValuationOutput.EXPECTED_SHORTFALL, ValuationOutput.GREEKS, ValuationOutput.PV),
)
