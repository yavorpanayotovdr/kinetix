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

data class RiskClassChargeItem(
    val riskClass: String,
    val deltaCharge: Double,
    val vegaCharge: Double,
    val curvatureCharge: Double,
    val totalCharge: Double,
)

data class FrtbResultSummary(
    val portfolioId: String,
    val sbmCharges: List<RiskClassChargeItem>,
    val totalSbmCharge: Double,
    val grossJtd: Double,
    val hedgeBenefit: Double,
    val netDrc: Double,
    val exoticNotional: Double,
    val otherNotional: Double,
    val totalRrao: Double,
    val totalCapitalCharge: Double,
    val calculatedAt: Instant,
)

data class ReportResult(
    val portfolioId: String,
    val format: String,
    val content: String,
    val generatedAt: Instant,
)

data class DependenciesParams(
    val portfolioId: String,
    val calculationType: String,
    val confidenceLevel: String,
)

data class MarketDataDependencyItem(
    val dataType: String,
    val instrumentId: String,
    val assetClass: String,
    val required: Boolean,
    val description: String,
    val parameters: Map<String, String>,
)

data class DataDependenciesSummary(
    val portfolioId: String,
    val dependencies: List<MarketDataDependencyItem>,
)

data class JobStepItem(
    val name: String,
    val status: String,
    val startedAt: Instant,
    val completedAt: Instant?,
    val durationMs: Long?,
    val details: Map<String, String>,
    val error: String?,
)

data class ValuationJobSummaryItem(
    val jobId: String,
    val portfolioId: String,
    val triggerType: String,
    val status: String,
    val startedAt: Instant,
    val completedAt: Instant?,
    val durationMs: Long?,
    val calculationType: String?,
    val varValue: Double?,
    val expectedShortfall: Double?,
)

data class ValuationJobDetailItem(
    val jobId: String,
    val portfolioId: String,
    val triggerType: String,
    val status: String,
    val startedAt: Instant,
    val completedAt: Instant?,
    val durationMs: Long?,
    val calculationType: String?,
    val confidenceLevel: String?,
    val varValue: Double?,
    val expectedShortfall: Double?,
    val steps: List<JobStepItem>,
    val error: String?,
)

interface RiskServiceClient {
    suspend fun calculateVaR(params: VaRCalculationParams): VaRResultSummary?
    suspend fun getLatestVaR(portfolioId: String): VaRResultSummary?
    suspend fun runStressTest(params: StressTestParams): StressTestResultSummary?
    suspend fun listScenarios(): List<String>
    suspend fun calculateGreeks(params: VaRCalculationParams): GreeksResultSummary?
    suspend fun calculateFrtb(portfolioId: String): FrtbResultSummary?
    suspend fun generateReport(portfolioId: String, format: String): ReportResult?
    suspend fun discoverDependencies(params: DependenciesParams): DataDependenciesSummary?
    suspend fun listValuationJobs(portfolioId: String, limit: Int = 20, offset: Int = 0, from: Instant? = null, to: Instant? = null): Pair<List<ValuationJobSummaryItem>, Long>
    suspend fun getValuationJobDetail(jobId: String): ValuationJobDetailItem?
}
