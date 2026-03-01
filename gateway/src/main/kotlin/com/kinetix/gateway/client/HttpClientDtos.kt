package com.kinetix.gateway.client

import com.kinetix.common.model.*
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

// --- Position Service DTOs ---

@Serializable
data class PortfolioSummaryDto(val portfolioId: String)

@Serializable
data class CurrencyExposureDto(
    val currency: String,
    val localValue: MoneyDto,
    val baseValue: MoneyDto,
    val fxRate: String,
)

@Serializable
data class PortfolioAggregationDto(
    val portfolioId: String,
    val baseCurrency: String,
    val totalNav: MoneyDto,
    val totalUnrealizedPnl: MoneyDto,
    val currencyBreakdown: List<CurrencyExposureDto>,
)

@Serializable
data class MoneyDto(val amount: String, val currency: String)

@Serializable
data class PositionDto(
    val portfolioId: String,
    val instrumentId: String,
    val assetClass: String,
    val quantity: String,
    val averageCost: MoneyDto,
    val marketPrice: MoneyDto,
    val marketValue: MoneyDto,
    val unrealizedPnl: MoneyDto,
)

@Serializable
data class TradeDto(
    val tradeId: String,
    val portfolioId: String,
    val instrumentId: String,
    val assetClass: String,
    val side: String,
    val quantity: String,
    val price: MoneyDto,
    val tradedAt: String,
)

@Serializable
data class BookTradeResponseDto(
    val trade: TradeDto,
    val position: PositionDto,
)

@Serializable
data class BookTradeRequestDto(
    val tradeId: String,
    val instrumentId: String,
    val assetClass: String,
    val side: String,
    val quantity: String,
    val priceAmount: String,
    val priceCurrency: String,
    val tradedAt: String,
)

// --- Price Service DTOs ---

@Serializable
data class PricePointDto(
    val instrumentId: String,
    val price: MoneyDto,
    val timestamp: String,
    val source: String,
)

// --- Risk Service DTOs ---

@Serializable
data class VaRCalculationRequestDto(
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
    val timeHorizonDays: String? = null,
    val numSimulations: String? = null,
    val requestedOutputs: List<String>? = null,
)

@Serializable
data class ComponentBreakdownDto(
    val assetClass: String,
    val varContribution: String,
    val percentageOfTotal: String,
)

@Serializable
data class ValuationResultDto(
    val portfolioId: String,
    val calculationType: String,
    val confidenceLevel: String,
    val varValue: String,
    val expectedShortfall: String,
    val componentBreakdown: List<ComponentBreakdownDto>,
    val calculatedAt: String,
    val greeks: GreeksResultDto? = null,
    val computedOutputs: List<String>? = null,
    val pvValue: String? = null,
)

@Serializable
data class StressTestRequestDto(
    val scenarioName: String,
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
    val timeHorizonDays: String? = null,
    val volShocks: Map<String, Double>? = null,
    val priceShocks: Map<String, Double>? = null,
    val description: String? = null,
)

@Serializable
data class AssetClassImpactDto(
    val assetClass: String,
    val baseExposure: String,
    val stressedExposure: String,
    val pnlImpact: String,
)

@Serializable
data class StressTestResultDto(
    val scenarioName: String,
    val baseVar: String,
    val stressedVar: String,
    val pnlImpact: String,
    val assetClassImpacts: List<AssetClassImpactDto>,
    val calculatedAt: String,
)

@Serializable
data class GreekValuesDto(
    val assetClass: String,
    val delta: String,
    val gamma: String,
    val vega: String,
)

@Serializable
data class GreeksResultDto(
    val portfolioId: String,
    val assetClassGreeks: List<GreekValuesDto>,
    val theta: String,
    val rho: String,
    val calculatedAt: String,
)

@Serializable
data class RiskClassChargeDto(
    val riskClass: String,
    val deltaCharge: String,
    val vegaCharge: String,
    val curvatureCharge: String,
    val totalCharge: String,
)

@Serializable
data class FrtbResultDto(
    val portfolioId: String,
    val sbmCharges: List<RiskClassChargeDto>,
    val totalSbmCharge: String,
    val grossJtd: String,
    val hedgeBenefit: String,
    val netDrc: String,
    val exoticNotional: String,
    val otherNotional: String,
    val totalRrao: String,
    val totalCapitalCharge: String,
    val calculatedAt: String,
)

@Serializable
data class GenerateReportRequestDto(val format: String? = null)

@Serializable
data class ReportResultDto(
    val portfolioId: String,
    val format: String,
    val content: String,
    val generatedAt: String,
)

// --- Dependencies DTOs ---

@Serializable
data class DependenciesRequestDto(
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
)

@Serializable
data class MarketDataDependencyItemDto(
    val dataType: String,
    val instrumentId: String,
    val assetClass: String,
    val required: Boolean,
    val description: String,
    val parameters: Map<String, String>,
)

@Serializable
data class DataDependenciesDto(
    val portfolioId: String,
    val dependencies: List<MarketDataDependencyItemDto>,
)

// --- Notification DTOs ---

@Serializable
data class AlertRuleDto(
    val id: String,
    val name: String,
    val type: String,
    val threshold: Double,
    val operator: String,
    val severity: String,
    val channels: List<String>,
    val enabled: Boolean,
)

@Serializable
data class AlertEventDto(
    val id: String,
    val ruleId: String,
    val ruleName: String,
    val type: String,
    val severity: String,
    val message: String,
    val currentValue: Double,
    val threshold: Double,
    val portfolioId: String,
    val triggeredAt: String,
)

@Serializable
data class CreateAlertRuleRequestDto(
    val name: String,
    val type: String,
    val threshold: Double,
    val operator: String,
    val severity: String,
    val channels: List<String>,
)

// --- Job History DTOs ---

@Serializable
data class JobStepClientDto(
    val name: String,
    val status: String,
    val startedAt: String,
    val completedAt: String? = null,
    val durationMs: Long? = null,
    val details: Map<String, String> = emptyMap(),
    val error: String? = null,
)

@Serializable
data class ValuationJobSummaryClientDto(
    val jobId: String,
    val portfolioId: String,
    val triggerType: String,
    val status: String,
    val startedAt: String,
    val completedAt: String? = null,
    val durationMs: Long? = null,
    val calculationType: String? = null,
    val varValue: Double? = null,
    val expectedShortfall: Double? = null,
    val pvValue: Double? = null,
)

@Serializable
data class ValuationJobDetailClientDto(
    val jobId: String,
    val portfolioId: String,
    val triggerType: String,
    val status: String,
    val startedAt: String,
    val completedAt: String? = null,
    val durationMs: Long? = null,
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
    val varValue: Double? = null,
    val expectedShortfall: Double? = null,
    val pvValue: Double? = null,
    val steps: List<JobStepClientDto> = emptyList(),
    val error: String? = null,
)

@Serializable
data class PaginatedJobsClientDto(
    val items: List<ValuationJobSummaryClientDto>,
    val totalCount: Long,
)

// --- SOD Snapshot DTOs ---

@Serializable
data class SodBaselineStatusClientDto(
    val exists: Boolean,
    val baselineDate: String? = null,
    val snapshotType: String? = null,
    val createdAt: String? = null,
    val sourceJobId: String? = null,
    val calculationType: String? = null,
)

// --- P&L Attribution DTOs ---

@Serializable
data class PositionPnlAttributionClientDto(
    val instrumentId: String,
    val assetClass: String,
    val totalPnl: String,
    val deltaPnl: String,
    val gammaPnl: String,
    val vegaPnl: String,
    val thetaPnl: String,
    val rhoPnl: String,
    val unexplainedPnl: String,
)

@Serializable
data class PnlAttributionClientDto(
    val portfolioId: String,
    val date: String,
    val totalPnl: String,
    val deltaPnl: String,
    val gammaPnl: String,
    val vegaPnl: String,
    val thetaPnl: String,
    val rhoPnl: String,
    val unexplainedPnl: String,
    val positionAttributions: List<PositionPnlAttributionClientDto>,
    val calculatedAt: String,
)

// --- Margin DTOs ---

@Serializable
data class MarginEstimateClientDto(
    val initialMargin: String,
    val variationMargin: String,
    val totalMargin: String,
    val currency: String,
)

// --- Domain mappers ---

fun PortfolioSummaryDto.toDomain() = PortfolioSummary(id = PortfolioId(portfolioId))

fun MoneyDto.toDomainMoney() = Money(BigDecimal(amount), Currency.getInstance(currency))

fun PositionDto.toDomain() = Position(
    portfolioId = PortfolioId(portfolioId),
    instrumentId = InstrumentId(instrumentId),
    assetClass = AssetClass.valueOf(assetClass),
    quantity = BigDecimal(quantity),
    averageCost = averageCost.toDomainMoney(),
    marketPrice = marketPrice.toDomainMoney(),
)

fun TradeDto.toDomain() = Trade(
    tradeId = TradeId(tradeId),
    portfolioId = PortfolioId(portfolioId),
    instrumentId = InstrumentId(instrumentId),
    assetClass = AssetClass.valueOf(assetClass),
    side = Side.valueOf(side),
    quantity = BigDecimal(quantity),
    price = price.toDomainMoney(),
    tradedAt = Instant.parse(tradedAt),
)

fun BookTradeResponseDto.toDomain() = BookTradeResult(
    trade = trade.toDomain(),
    position = position.toDomain(),
)

fun PricePointDto.toDomain() = PricePoint(
    instrumentId = InstrumentId(instrumentId),
    price = price.toDomainMoney(),
    timestamp = Instant.parse(timestamp),
    source = PriceSource.valueOf(source),
)

fun ValuationResultDto.toDomain() = ValuationResultSummary(
    portfolioId = portfolioId,
    calculationType = calculationType,
    confidenceLevel = confidenceLevel,
    varValue = varValue.toDouble(),
    expectedShortfall = expectedShortfall.toDouble(),
    componentBreakdown = componentBreakdown.map {
        ComponentBreakdownItem(
            assetClass = it.assetClass,
            varContribution = it.varContribution.toDouble(),
            percentageOfTotal = it.percentageOfTotal.toDouble(),
        )
    },
    calculatedAt = Instant.parse(calculatedAt),
    greeks = greeks?.toDomain(),
    pvValue = pvValue?.toDoubleOrNull(),
)

fun StressTestResultDto.toDomain() = StressTestResultSummary(
    scenarioName = scenarioName,
    baseVar = baseVar.toDouble(),
    stressedVar = stressedVar.toDouble(),
    pnlImpact = pnlImpact.toDouble(),
    assetClassImpacts = assetClassImpacts.map {
        AssetClassImpactItem(
            assetClass = it.assetClass,
            baseExposure = it.baseExposure.toDouble(),
            stressedExposure = it.stressedExposure.toDouble(),
            pnlImpact = it.pnlImpact.toDouble(),
        )
    },
    calculatedAt = Instant.parse(calculatedAt),
)

fun GreeksResultDto.toDomain() = GreeksResultSummary(
    portfolioId = portfolioId,
    assetClassGreeks = assetClassGreeks.map {
        GreekValuesItem(
            assetClass = it.assetClass,
            delta = it.delta.toDouble(),
            gamma = it.gamma.toDouble(),
            vega = it.vega.toDouble(),
        )
    },
    theta = theta.toDouble(),
    rho = rho.toDouble(),
    calculatedAt = Instant.parse(calculatedAt),
)

fun FrtbResultDto.toDomain() = FrtbResultSummary(
    portfolioId = portfolioId,
    sbmCharges = sbmCharges.map {
        RiskClassChargeItem(
            riskClass = it.riskClass,
            deltaCharge = it.deltaCharge.toDouble(),
            vegaCharge = it.vegaCharge.toDouble(),
            curvatureCharge = it.curvatureCharge.toDouble(),
            totalCharge = it.totalCharge.toDouble(),
        )
    },
    totalSbmCharge = totalSbmCharge.toDouble(),
    grossJtd = grossJtd.toDouble(),
    hedgeBenefit = hedgeBenefit.toDouble(),
    netDrc = netDrc.toDouble(),
    exoticNotional = exoticNotional.toDouble(),
    otherNotional = otherNotional.toDouble(),
    totalRrao = totalRrao.toDouble(),
    totalCapitalCharge = totalCapitalCharge.toDouble(),
    calculatedAt = Instant.parse(calculatedAt),
)

fun ReportResultDto.toDomain() = ReportResult(
    portfolioId = portfolioId,
    format = format,
    content = content,
    generatedAt = Instant.parse(generatedAt),
)

fun DataDependenciesDto.toDomain() = DataDependenciesSummary(
    portfolioId = portfolioId,
    dependencies = dependencies.map {
        MarketDataDependencyItem(
            dataType = it.dataType,
            instrumentId = it.instrumentId,
            assetClass = it.assetClass,
            required = it.required,
            description = it.description,
            parameters = it.parameters,
        )
    },
)

fun AlertRuleDto.toDomain() = AlertRuleItem(
    id = id,
    name = name,
    type = type,
    threshold = threshold,
    operator = operator,
    severity = severity,
    channels = channels,
    enabled = enabled,
)

fun AlertEventDto.toDomain() = AlertEventItem(
    id = id,
    ruleId = ruleId,
    ruleName = ruleName,
    type = type,
    severity = severity,
    message = message,
    currentValue = currentValue,
    threshold = threshold,
    portfolioId = portfolioId,
    triggeredAt = Instant.parse(triggeredAt),
)

fun JobStepClientDto.toDomain() = JobStepItem(
    name = name,
    status = status,
    startedAt = Instant.parse(startedAt),
    completedAt = completedAt?.let { Instant.parse(it) },
    durationMs = durationMs,
    details = details,
    error = error,
)

fun ValuationJobSummaryClientDto.toDomain() = ValuationJobSummaryItem(
    jobId = jobId,
    portfolioId = portfolioId,
    triggerType = triggerType,
    status = status,
    startedAt = Instant.parse(startedAt),
    completedAt = completedAt?.let { Instant.parse(it) },
    durationMs = durationMs,
    calculationType = calculationType,
    varValue = varValue,
    expectedShortfall = expectedShortfall,
    pvValue = pvValue,
)

fun ValuationJobDetailClientDto.toDomain() = ValuationJobDetailItem(
    jobId = jobId,
    portfolioId = portfolioId,
    triggerType = triggerType,
    status = status,
    startedAt = Instant.parse(startedAt),
    completedAt = completedAt?.let { Instant.parse(it) },
    durationMs = durationMs,
    calculationType = calculationType,
    confidenceLevel = confidenceLevel,
    varValue = varValue,
    expectedShortfall = expectedShortfall,
    pvValue = pvValue,
    steps = steps.map { it.toDomain() },
    error = error,
)

fun SodBaselineStatusClientDto.toDomain() = SodBaselineStatusSummary(
    exists = exists,
    baselineDate = baselineDate,
    snapshotType = snapshotType,
    createdAt = createdAt,
    sourceJobId = sourceJobId,
    calculationType = calculationType,
)

fun PositionPnlAttributionClientDto.toDomain() = PositionPnlAttributionSummary(
    instrumentId = instrumentId,
    assetClass = assetClass,
    totalPnl = totalPnl,
    deltaPnl = deltaPnl,
    gammaPnl = gammaPnl,
    vegaPnl = vegaPnl,
    thetaPnl = thetaPnl,
    rhoPnl = rhoPnl,
    unexplainedPnl = unexplainedPnl,
)

fun PnlAttributionClientDto.toDomain() = PnlAttributionSummary(
    portfolioId = portfolioId,
    date = date,
    totalPnl = totalPnl,
    deltaPnl = deltaPnl,
    gammaPnl = gammaPnl,
    vegaPnl = vegaPnl,
    thetaPnl = thetaPnl,
    rhoPnl = rhoPnl,
    unexplainedPnl = unexplainedPnl,
    positionAttributions = positionAttributions.map { it.toDomain() },
    calculatedAt = calculatedAt,
)

fun MarginEstimateClientDto.toDomain() = MarginEstimateSummary(
    initialMargin = initialMargin,
    variationMargin = variationMargin,
    totalMargin = totalMargin,
    currency = currency,
)

fun CurrencyExposureDto.toDomain() = CurrencyExposureSummary(
    currency = currency,
    localValue = localValue.toDomainMoney(),
    baseValue = baseValue.toDomainMoney(),
    fxRate = BigDecimal(fxRate),
)

fun PortfolioAggregationDto.toDomain() = PortfolioAggregationSummary(
    portfolioId = portfolioId,
    baseCurrency = baseCurrency,
    totalNav = totalNav.toDomainMoney(),
    totalUnrealizedPnl = totalUnrealizedPnl.toDomainMoney(),
    currencyBreakdown = currencyBreakdown.map { it.toDomain() },
)
