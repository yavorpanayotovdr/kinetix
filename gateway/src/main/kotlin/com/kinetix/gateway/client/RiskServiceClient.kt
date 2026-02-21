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

data class StressTestParams(
    val portfolioId: String,
    val scenarioName: String,
    val calculationType: String,
    val confidenceLevel: String,
    val timeHorizonDays: Int,
    val volShocks: Map<String, Double>?,
    val priceShocks: Map<String, Double>?,
    val description: String?,
)

data class AssetClassImpactItem(
    val assetClass: String,
    val baseExposure: Double,
    val stressedExposure: Double,
    val pnlImpact: Double,
)

data class StressTestResultSummary(
    val scenarioName: String,
    val baseVar: Double,
    val stressedVar: Double,
    val pnlImpact: Double,
    val assetClassImpacts: List<AssetClassImpactItem>,
    val calculatedAt: Instant,
)

data class GreekValuesItem(
    val assetClass: String,
    val delta: Double,
    val gamma: Double,
    val vega: Double,
)

data class GreeksResultSummary(
    val portfolioId: String,
    val assetClassGreeks: List<GreekValuesItem>,
    val theta: Double,
    val rho: Double,
    val calculatedAt: Instant,
)

interface RiskServiceClient {
    suspend fun calculateVaR(params: VaRCalculationParams): VaRResultSummary?
    suspend fun getLatestVaR(portfolioId: String): VaRResultSummary?
    suspend fun runStressTest(params: StressTestParams): StressTestResultSummary?
    suspend fun listScenarios(): List<String>
    suspend fun calculateGreeks(params: VaRCalculationParams): GreeksResultSummary?
}
