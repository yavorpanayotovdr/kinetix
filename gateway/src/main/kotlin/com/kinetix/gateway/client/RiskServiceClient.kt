package com.kinetix.gateway.client

import java.time.Instant

data class VaRCalculationParams(
    val portfolioId: String,
    val calculationType: String,
    val confidenceLevel: String,
    val timeHorizonDays: Int,
    val numSimulations: Int,
)

data class ComponentBreakdownItem(
    val assetClass: String,
    val varContribution: Double,
    val percentageOfTotal: Double,
)

data class VaRResultSummary(
    val portfolioId: String,
    val calculationType: String,
    val confidenceLevel: String,
    val varValue: Double,
    val expectedShortfall: Double,
    val componentBreakdown: List<ComponentBreakdownItem>,
    val calculatedAt: Instant,
)

interface RiskServiceClient {
    suspend fun calculateVaR(params: VaRCalculationParams): VaRResultSummary?
    suspend fun getLatestVaR(portfolioId: String): VaRResultSummary?
}
