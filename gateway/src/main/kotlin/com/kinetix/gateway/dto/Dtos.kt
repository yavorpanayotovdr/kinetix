package com.kinetix.gateway.dto

import com.kinetix.common.model.*
import com.kinetix.gateway.client.AlertEventItem
import com.kinetix.gateway.client.ChartDataPointItem
import com.kinetix.gateway.client.ChartDataSummary
import com.kinetix.gateway.client.AlertRuleItem
import com.kinetix.gateway.client.ValuationJobDetailItem
import com.kinetix.gateway.client.ValuationJobSummaryItem
import com.kinetix.gateway.client.JobPhaseItem
import com.kinetix.gateway.client.BookTradeCommand
import com.kinetix.gateway.client.BookTradeResult
import com.kinetix.gateway.client.DataDependenciesSummary
import com.kinetix.gateway.client.DependenciesParams
import com.kinetix.gateway.client.MarketDataDependencyItem
import com.kinetix.gateway.client.AssetClassImpactItem
import com.kinetix.gateway.client.ComponentBreakdownItem
import com.kinetix.gateway.client.CreateAlertRuleParams
import com.kinetix.gateway.client.PositionStressImpactItem
import com.kinetix.gateway.client.StressLimitBreachItem
import com.kinetix.gateway.client.StressTestBatchParams
import com.kinetix.gateway.client.CurrencyExposureSummary
import com.kinetix.gateway.client.FrtbResultSummary
import com.kinetix.gateway.client.GreekValuesItem
import com.kinetix.gateway.client.GreeksResultSummary
import com.kinetix.gateway.client.PnlAttributionSummary
import com.kinetix.gateway.client.PortfolioAggregationSummary
import com.kinetix.gateway.client.PortfolioSummary
import com.kinetix.gateway.client.PositionPnlAttributionSummary
import com.kinetix.gateway.client.ReportResult
import com.kinetix.gateway.client.RiskClassChargeItem
import com.kinetix.gateway.client.SodBaselineStatusSummary
import com.kinetix.gateway.client.StressScenarioItem
import com.kinetix.gateway.client.HistoricalReplayParams
import com.kinetix.gateway.client.HistoricalReplayResultSummary
import com.kinetix.gateway.client.InstrumentDailyReturnsParam
import com.kinetix.gateway.client.InstrumentShockSummary
import com.kinetix.gateway.client.PositionReplayImpactSummary
import com.kinetix.gateway.client.ReverseStressParams
import com.kinetix.gateway.client.ReverseStressResultSummary
import com.kinetix.gateway.client.StressTestParams
import com.kinetix.gateway.client.StressTestResultSummary
import com.kinetix.gateway.client.VaRCalculationParams
import com.kinetix.gateway.client.ValuationResultSummary
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

// --- Request DTOs ---

@Serializable
data class BookTradeRequest(
    val tradeId: String,
    val instrumentId: String,
    val assetClass: String,
    val side: String,
    val quantity: String,
    val priceAmount: String,
    val priceCurrency: String,
    val tradedAt: String,
)

// --- Response DTOs ---

@Serializable
data class MoneyDto(
    val amount: String,
    val currency: String,
)

@Serializable
data class TradeResponse(
    val tradeId: String,
    val bookId: String,
    val instrumentId: String,
    val assetClass: String,
    val side: String,
    val quantity: String,
    val price: MoneyDto,
    val tradedAt: String,
    val instrumentType: String? = null,
)

@Serializable
data class PositionResponse(
    val bookId: String,
    val instrumentId: String,
    val assetClass: String,
    val quantity: String,
    val averageCost: MoneyDto,
    val marketPrice: MoneyDto,
    val marketValue: MoneyDto,
    val unrealizedPnl: MoneyDto,
    val instrumentType: String? = null,
    val displayName: String? = null,
)

@Serializable
data class BookTradeResponse(
    val trade: TradeResponse,
    val position: PositionResponse,
)

@Serializable
data class PortfolioSummaryResponse(
    val bookId: String,
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
)

@Serializable
data class CurrencyExposureResponse(
    val currency: String,
    val localValue: MoneyDto,
    val baseValue: MoneyDto,
    val fxRate: String,
)

@Serializable
data class PortfolioAggregationResponse(
    val bookId: String,
    val baseCurrency: String,
    val totalNav: MoneyDto,
    val totalUnrealizedPnl: MoneyDto,
    val currencyBreakdown: List<CurrencyExposureResponse>,
)

// --- Domain -> DTO mappers ---

fun Money.toDto(): MoneyDto = MoneyDto(
    amount = amount.toPlainString(),
    currency = currency.currencyCode,
)

fun Trade.toResponse(): TradeResponse = TradeResponse(
    tradeId = tradeId.value,
    bookId = bookId.value,
    instrumentId = instrumentId.value,
    assetClass = assetClass.name,
    side = side.name,
    quantity = quantity.toPlainString(),
    price = price.toDto(),
    tradedAt = tradedAt.toString(),
    instrumentType = instrumentType,
)

fun Position.toResponse(): PositionResponse = PositionResponse(
    bookId = bookId.value,
    instrumentId = instrumentId.value,
    assetClass = assetClass.name,
    quantity = quantity.toPlainString(),
    averageCost = averageCost.toDto(),
    marketPrice = marketPrice.toDto(),
    marketValue = marketValue.toDto(),
    unrealizedPnl = unrealizedPnl.toDto(),
    instrumentType = instrumentType,
)

fun BookTradeResult.toResponse(): BookTradeResponse = BookTradeResponse(
    trade = trade.toResponse(),
    position = position.toResponse(),
)

fun PortfolioSummary.toResponse(): PortfolioSummaryResponse = PortfolioSummaryResponse(
    bookId = id.value,
)

fun CurrencyExposureSummary.toResponse(): CurrencyExposureResponse = CurrencyExposureResponse(
    currency = currency,
    localValue = localValue.toDto(),
    baseValue = baseValue.toDto(),
    fxRate = fxRate.toPlainString(),
)

fun PortfolioAggregationSummary.toResponse(): PortfolioAggregationResponse = PortfolioAggregationResponse(
    bookId = bookId,
    baseCurrency = baseCurrency,
    totalNav = totalNav.toDto(),
    totalUnrealizedPnl = totalUnrealizedPnl.toDto(),
    currencyBreakdown = currencyBreakdown.map { it.toResponse() },
)

// --- DTO -> Domain mappers ---

fun BookTradeRequest.toCommand(bookId: BookId): BookTradeCommand {
    val qty = BigDecimal(quantity)
    require(qty > BigDecimal.ZERO) { "Trade quantity must be positive, was $qty" }
    val priceAmt = BigDecimal(priceAmount)
    require(priceAmt >= BigDecimal.ZERO) { "Trade price must be non-negative, was $priceAmt" }
    return BookTradeCommand(
        tradeId = TradeId(tradeId),
        bookId = bookId,
        instrumentId = InstrumentId(instrumentId),
        assetClass = AssetClass.valueOf(assetClass),
        side = Side.valueOf(side),
        quantity = qty,
        price = Money(priceAmt, Currency.getInstance(priceCurrency)),
        tradedAt = Instant.parse(tradedAt),
    )
}

// --- VaR DTOs ---

@Serializable
data class VaRCalculationRequest(
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
data class ValuationResultResponse(
    val bookId: String,
    val calculationType: String,
    val confidenceLevel: String,
    val varValue: String,
    val expectedShortfall: String,
    val componentBreakdown: List<ComponentBreakdownDto>,
    val calculatedAt: String,
    val greeks: GreeksResponse? = null,
    val computedOutputs: List<String>? = null,
    val pvValue: String? = null,
    val valuationDate: String? = null,
)

// --- VaR mappers ---

private val validCalculationTypes = setOf("HISTORICAL", "PARAMETRIC", "MONTE_CARLO")
private val validConfidenceLevels = setOf("CL_95", "CL_975", "CL_99")

fun VaRCalculationRequest.toParams(bookId: String): VaRCalculationParams {
    val calcType = calculationType ?: "PARAMETRIC"
    require(calcType in validCalculationTypes) {
        "Invalid calculationType: $calcType. Must be one of $validCalculationTypes"
    }
    val confLevel = confidenceLevel ?: "CL_95"
    require(confLevel in validConfidenceLevels) {
        "Invalid confidenceLevel: $confLevel. Must be one of $validConfidenceLevels"
    }
    return VaRCalculationParams(
        bookId = bookId,
        calculationType = calcType,
        confidenceLevel = confLevel,
        timeHorizonDays = timeHorizonDays?.toInt() ?: 1,
        numSimulations = numSimulations?.toInt() ?: 10_000,
        requestedOutputs = requestedOutputs,
    )
}

fun ComponentBreakdownItem.toDto(): ComponentBreakdownDto = ComponentBreakdownDto(
    assetClass = assetClass,
    varContribution = "%.2f".format(varContribution),
    percentageOfTotal = "%.2f".format(percentageOfTotal),
)

fun ValuationResultSummary.toResponse(): ValuationResultResponse = ValuationResultResponse(
    bookId = bookId,
    calculationType = calculationType,
    confidenceLevel = confidenceLevel,
    varValue = "%.2f".format(varValue),
    expectedShortfall = "%.2f".format(expectedShortfall),
    componentBreakdown = componentBreakdown.map { it.toDto() },
    calculatedAt = calculatedAt.toString(),
    greeks = greeks?.toResponse(),
    pvValue = pvValue?.let { "%.2f".format(it) },
    valuationDate = valuationDate,
)

// --- Stress Test DTOs ---

@Serializable
data class StressTestRequest(
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
data class PositionStressImpactDto(
    val instrumentId: String,
    val assetClass: String,
    val baseMarketValue: String,
    val stressedMarketValue: String,
    val pnlImpact: String,
    val percentageOfTotal: String,
)

@Serializable
data class StressLimitBreachDto(
    val limitType: String,
    val limitLevel: String,
    val limitValue: String,
    val stressedValue: String,
    val breachSeverity: String,
    val scenarioName: String,
)

@Serializable
data class StressTestResponse(
    val scenarioName: String,
    val baseVar: String,
    val stressedVar: String,
    val pnlImpact: String,
    val assetClassImpacts: List<AssetClassImpactDto>,
    val calculatedAt: String,
    val positionImpacts: List<PositionStressImpactDto> = emptyList(),
    val limitBreaches: List<StressLimitBreachDto> = emptyList(),
)

@Serializable
data class StressTestBatchRequest(
    val scenarioNames: List<String>,
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
    val timeHorizonDays: String? = null,
)

@Serializable
data class GreekValuesDto(
    val assetClass: String,
    val delta: String,
    val gamma: String,
    val vega: String,
)

@Serializable
data class GreeksResponse(
    val bookId: String,
    val assetClassGreeks: List<GreekValuesDto>,
    val theta: String,
    val rho: String,
    val calculatedAt: String,
)

// --- Stress Test mappers ---

fun StressTestRequest.toParams(bookId: String): StressTestParams {
    val calcType = calculationType ?: "PARAMETRIC"
    require(calcType in validCalculationTypes) {
        "Invalid calculationType: $calcType. Must be one of $validCalculationTypes"
    }
    val confLevel = confidenceLevel ?: "CL_95"
    require(confLevel in validConfidenceLevels) {
        "Invalid confidenceLevel: $confLevel. Must be one of $validConfidenceLevels"
    }
    return StressTestParams(
        bookId = bookId,
        scenarioName = scenarioName,
        calculationType = calcType,
        confidenceLevel = confLevel,
        timeHorizonDays = timeHorizonDays?.toInt() ?: 1,
        volShocks = volShocks,
        priceShocks = priceShocks,
        description = description,
    )
}

fun AssetClassImpactItem.toDto(): AssetClassImpactDto = AssetClassImpactDto(
    assetClass = assetClass,
    baseExposure = "%.2f".format(baseExposure),
    stressedExposure = "%.2f".format(stressedExposure),
    pnlImpact = "%.2f".format(pnlImpact),
)

fun PositionStressImpactItem.toDto(): PositionStressImpactDto = PositionStressImpactDto(
    instrumentId = instrumentId,
    assetClass = assetClass,
    baseMarketValue = "%.2f".format(baseMarketValue),
    stressedMarketValue = "%.2f".format(stressedMarketValue),
    pnlImpact = "%.2f".format(pnlImpact),
    percentageOfTotal = "%.2f".format(percentageOfTotal),
)

fun StressLimitBreachItem.toDto(): StressLimitBreachDto = StressLimitBreachDto(
    limitType = limitType,
    limitLevel = limitLevel,
    limitValue = limitValue,
    stressedValue = stressedValue,
    breachSeverity = breachSeverity,
    scenarioName = scenarioName,
)

fun StressTestResultSummary.toResponse(): StressTestResponse = StressTestResponse(
    scenarioName = scenarioName,
    baseVar = "%.2f".format(baseVar),
    stressedVar = "%.2f".format(stressedVar),
    pnlImpact = "%.2f".format(pnlImpact),
    assetClassImpacts = assetClassImpacts.map { it.toDto() },
    calculatedAt = calculatedAt.toString(),
    positionImpacts = positionImpacts.map { it.toDto() },
    limitBreaches = limitBreaches.map { it.toDto() },
)

fun StressTestBatchRequest.toParams(bookId: String): StressTestBatchParams {
    val calcType = calculationType ?: "PARAMETRIC"
    require(calcType in validCalculationTypes) {
        "Invalid calculationType: $calcType. Must be one of $validCalculationTypes"
    }
    val confLevel = confidenceLevel ?: "CL_95"
    require(confLevel in validConfidenceLevels) {
        "Invalid confidenceLevel: $confLevel. Must be one of $validConfidenceLevels"
    }
    return StressTestBatchParams(
        bookId = bookId,
        scenarioNames = scenarioNames,
        calculationType = calcType,
        confidenceLevel = confLevel,
        timeHorizonDays = timeHorizonDays?.toInt() ?: 1,
    )
}

fun GreekValuesItem.toDto(): GreekValuesDto = GreekValuesDto(
    assetClass = assetClass,
    delta = "%.6f".format(delta),
    gamma = "%.6f".format(gamma),
    vega = "%.6f".format(vega),
)

fun GreeksResultSummary.toResponse(): GreeksResponse = GreeksResponse(
    bookId = bookId,
    assetClassGreeks = assetClassGreeks.map { it.toDto() },
    theta = "%.6f".format(theta),
    rho = "%.6f".format(rho),
    calculatedAt = calculatedAt.toString(),
)

// --- Regulatory / FRTB DTOs ---

@Serializable
data class RiskClassChargeDto(
    val riskClass: String,
    val deltaCharge: String,
    val vegaCharge: String,
    val curvatureCharge: String,
    val totalCharge: String,
)

@Serializable
data class FrtbResultResponse(
    val bookId: String,
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
data class GenerateReportRequest(
    val format: String? = null,
)

@Serializable
data class ReportResponse(
    val bookId: String,
    val format: String,
    val content: String,
    val generatedAt: String,
)

// --- Regulatory mappers ---

fun RiskClassChargeItem.toDto(): RiskClassChargeDto = RiskClassChargeDto(
    riskClass = riskClass,
    deltaCharge = "%.2f".format(deltaCharge),
    vegaCharge = "%.2f".format(vegaCharge),
    curvatureCharge = "%.2f".format(curvatureCharge),
    totalCharge = "%.2f".format(totalCharge),
)

fun FrtbResultSummary.toResponse(): FrtbResultResponse = FrtbResultResponse(
    bookId = bookId,
    sbmCharges = sbmCharges.map { it.toDto() },
    totalSbmCharge = "%.2f".format(totalSbmCharge),
    grossJtd = "%.2f".format(grossJtd),
    hedgeBenefit = "%.2f".format(hedgeBenefit),
    netDrc = "%.2f".format(netDrc),
    exoticNotional = "%.2f".format(exoticNotional),
    otherNotional = "%.2f".format(otherNotional),
    totalRrao = "%.2f".format(totalRrao),
    totalCapitalCharge = "%.2f".format(totalCapitalCharge),
    calculatedAt = calculatedAt.toString(),
)

fun ReportResult.toResponse(): ReportResponse = ReportResponse(
    bookId = bookId,
    format = format,
    content = content,
    generatedAt = generatedAt.toString(),
)

// --- Dependencies DTOs ---

@Serializable
data class DependenciesRequest(
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
)

@Serializable
data class MarketDataDependencyResponse(
    val dataType: String,
    val instrumentId: String,
    val assetClass: String,
    val required: Boolean,
    val description: String,
    val parameters: Map<String, String>,
)

@Serializable
data class DataDependenciesResponse(
    val bookId: String,
    val dependencies: List<MarketDataDependencyResponse>,
)

// --- Dependencies mappers ---

fun DependenciesRequest.toParams(bookId: String): DependenciesParams {
    val calcType = calculationType ?: "PARAMETRIC"
    require(calcType in validCalculationTypes) {
        "Invalid calculationType: $calcType. Must be one of $validCalculationTypes"
    }
    val confLevel = confidenceLevel ?: "CL_95"
    require(confLevel in validConfidenceLevels) {
        "Invalid confidenceLevel: $confLevel. Must be one of $validConfidenceLevels"
    }
    return DependenciesParams(
        bookId = bookId,
        calculationType = calcType,
        confidenceLevel = confLevel,
    )
}

fun MarketDataDependencyItem.toDto(): MarketDataDependencyResponse = MarketDataDependencyResponse(
    dataType = dataType,
    instrumentId = instrumentId,
    assetClass = assetClass,
    required = required,
    description = description,
    parameters = parameters,
)

fun DataDependenciesSummary.toResponse(): DataDependenciesResponse = DataDependenciesResponse(
    bookId = bookId,
    dependencies = dependencies.map { it.toDto() },
)

// --- Alert / Notification DTOs ---

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
    val bookId: String,
    val triggeredAt: String,
    val status: String = "TRIGGERED",
    val resolvedAt: String? = null,
    val resolvedReason: String? = null,
    val correlationId: String? = null,
    val suggestedAction: String? = null,
)

@Serializable
data class CreateAlertRuleRequest(
    val name: String,
    val type: String,
    val threshold: Double,
    val operator: String,
    val severity: String,
    val channels: List<String>,
)

@Serializable
data class AcknowledgeAlertRequest(
    val acknowledgedBy: String,
    val notes: String? = null,
)

// --- Alert mappers ---

fun AlertRuleItem.toDto(): AlertRuleDto = AlertRuleDto(
    id = id,
    name = name,
    type = type,
    threshold = threshold,
    operator = operator,
    severity = severity,
    channels = channels,
    enabled = enabled,
)

fun AlertEventItem.toDto(): AlertEventDto = AlertEventDto(
    id = id,
    ruleId = ruleId,
    ruleName = ruleName,
    type = type,
    severity = severity,
    message = message,
    currentValue = currentValue,
    threshold = threshold,
    bookId = bookId,
    triggeredAt = triggeredAt.toString(),
    status = status,
    resolvedAt = resolvedAt?.toString(),
    resolvedReason = resolvedReason,
    correlationId = correlationId,
    suggestedAction = suggestedAction,
)

fun CreateAlertRuleRequest.toParams(): CreateAlertRuleParams = CreateAlertRuleParams(
    name = name,
    type = type,
    threshold = threshold,
    operator = operator,
    severity = severity,
    channels = channels,
)

// --- SOD Snapshot DTOs ---

@Serializable
data class SodBaselineStatusResponse(
    val exists: Boolean,
    val baselineDate: String? = null,
    val snapshotType: String? = null,
    val createdAt: String? = null,
    val sourceJobId: String? = null,
    val calculationType: String? = null,
)

// --- SOD Snapshot mappers ---

fun SodBaselineStatusSummary.toResponse(): SodBaselineStatusResponse = SodBaselineStatusResponse(
    exists = exists,
    baselineDate = baselineDate,
    snapshotType = snapshotType,
    createdAt = createdAt,
    sourceJobId = sourceJobId,
    calculationType = calculationType,
)

// --- P&L Attribution DTOs ---

@Serializable
data class PositionPnlAttributionResponse(
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
data class PnlAttributionResponse(
    val bookId: String,
    val date: String,
    val totalPnl: String,
    val deltaPnl: String,
    val gammaPnl: String,
    val vegaPnl: String,
    val thetaPnl: String,
    val rhoPnl: String,
    val unexplainedPnl: String,
    val positionAttributions: List<PositionPnlAttributionResponse>,
    val calculatedAt: String,
)

// --- P&L Attribution mappers ---

fun PositionPnlAttributionSummary.toResponse(): PositionPnlAttributionResponse = PositionPnlAttributionResponse(
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

fun PnlAttributionSummary.toResponse(): PnlAttributionResponse = PnlAttributionResponse(
    bookId = bookId,
    date = date,
    totalPnl = totalPnl,
    deltaPnl = deltaPnl,
    gammaPnl = gammaPnl,
    vegaPnl = vegaPnl,
    thetaPnl = thetaPnl,
    rhoPnl = rhoPnl,
    unexplainedPnl = unexplainedPnl,
    positionAttributions = positionAttributions.map { it.toResponse() },
    calculatedAt = calculatedAt,
)

// --- What-If DTOs ---

@Serializable
data class WhatIfGatewayTradeDto(
    val instrumentId: String,
    val assetClass: String,
    val side: String,
    val quantity: String,
    val priceAmount: String,
    val priceCurrency: String,
)

@Serializable
data class WhatIfGatewayRequest(
    val hypotheticalTrades: List<WhatIfGatewayTradeDto>,
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
)

@Serializable
data class WhatIfGatewayPositionRiskDto(
    val instrumentId: String,
    val assetClass: String,
    val marketValue: String,
    val delta: String? = null,
    val gamma: String? = null,
    val vega: String? = null,
    val varContribution: String,
    val esContribution: String,
    val percentageOfTotal: String,
)

@Serializable
data class WhatIfGatewayResponse(
    val baseVaR: String,
    val baseExpectedShortfall: String,
    val baseGreeks: GreeksResponse? = null,
    val basePositionRisk: List<WhatIfGatewayPositionRiskDto>,
    val hypotheticalVaR: String,
    val hypotheticalExpectedShortfall: String,
    val hypotheticalGreeks: GreeksResponse? = null,
    val hypotheticalPositionRisk: List<WhatIfGatewayPositionRiskDto>,
    val varChange: String,
    val esChange: String,
    val calculatedAt: String,
)

// --- What-If mappers ---

fun WhatIfGatewayRequest.toParams(bookId: String): com.kinetix.gateway.client.WhatIfRequestParams =
    com.kinetix.gateway.client.WhatIfRequestParams(
        bookId = bookId,
        hypotheticalTrades = hypotheticalTrades.map {
            com.kinetix.gateway.client.HypotheticalTradeParam(
                instrumentId = it.instrumentId,
                assetClass = it.assetClass,
                side = it.side,
                quantity = it.quantity,
                priceAmount = it.priceAmount,
                priceCurrency = it.priceCurrency,
            )
        },
        calculationType = calculationType,
        confidenceLevel = confidenceLevel,
    )

fun com.kinetix.gateway.client.PositionRiskSummaryItem.toPositionRiskResponse(): WhatIfGatewayPositionRiskDto =
    toWhatIfDto()

fun com.kinetix.gateway.client.PositionRiskSummaryItem.toWhatIfDto(): WhatIfGatewayPositionRiskDto =
    WhatIfGatewayPositionRiskDto(
        instrumentId = instrumentId,
        assetClass = assetClass,
        marketValue = marketValue,
        delta = delta,
        gamma = gamma,
        vega = vega,
        varContribution = varContribution,
        esContribution = esContribution,
        percentageOfTotal = percentageOfTotal,
    )

fun com.kinetix.gateway.client.WhatIfResultSummary.toResponse(): WhatIfGatewayResponse =
    WhatIfGatewayResponse(
        baseVaR = baseVaR,
        baseExpectedShortfall = baseExpectedShortfall,
        baseGreeks = baseGreeks?.toResponse(),
        basePositionRisk = basePositionRisk.map { it.toWhatIfDto() },
        hypotheticalVaR = hypotheticalVaR,
        hypotheticalExpectedShortfall = hypotheticalExpectedShortfall,
        hypotheticalGreeks = hypotheticalGreeks?.toResponse(),
        hypotheticalPositionRisk = hypotheticalPositionRisk.map { it.toWhatIfDto() },
        varChange = varChange,
        esChange = esChange,
        calculatedAt = calculatedAt,
    )

// --- Data Quality DTOs ---

@Serializable
data class DataQualityCheckResponse(
    val name: String,
    val status: String,
    val message: String,
    val lastChecked: String,
)

@Serializable
data class DataQualityStatusResponse(
    val overall: String,
    val checks: List<DataQualityCheckResponse>,
)

// --- Job History DTOs ---

@Serializable
data class JobPhaseDto(
    val name: String,
    val status: String,
    val startedAt: String,
    val completedAt: String? = null,
    val durationMs: Long? = null,
    val details: Map<String, String> = emptyMap(),
    val error: String? = null,
)

@Serializable
data class ValuationJobSummaryResponse(
    val jobId: String,
    val bookId: String,
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
    val delta: Double? = null,
    val gamma: Double? = null,
    val vega: Double? = null,
    val theta: Double? = null,
    val rho: Double? = null,
    val valuationDate: String? = null,
    val runLabel: String? = null,
    val promotedAt: String? = null,
    val promotedBy: String? = null,
    val currentPhase: String? = null,
    val manifestId: String? = null,
)

@Serializable
data class ValuationJobDetailResponse(
    val jobId: String,
    val bookId: String,
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
    val phases: List<JobPhaseDto> = emptyList(),
    val error: String? = null,
    val valuationDate: String? = null,
    val runLabel: String? = null,
    val promotedAt: String? = null,
    val promotedBy: String? = null,
    val currentPhase: String? = null,
    val manifestId: String? = null,
)

@Serializable
data class PaginatedJobsResponse(
    val items: List<ValuationJobSummaryResponse>,
    val totalCount: Long,
)

@Serializable
data class ChartDataPointResponse(
    val bucket: String,
    val varValue: Double? = null,
    val expectedShortfall: Double? = null,
    val confidenceLevel: String? = null,
    val delta: Double? = null,
    val gamma: Double? = null,
    val vega: Double? = null,
    val theta: Double? = null,
    val rho: Double? = null,
    val pvValue: Double? = null,
    val jobCount: Int,
    val completedCount: Int,
    val failedCount: Int,
    val runningCount: Int,
)

@Serializable
data class ChartDataGatewayResponse(
    val points: List<ChartDataPointResponse>,
    val bucketSizeMs: Long,
)

fun ChartDataPointItem.toResponse(): ChartDataPointResponse = ChartDataPointResponse(
    bucket = bucket,
    varValue = varValue,
    expectedShortfall = expectedShortfall,
    confidenceLevel = confidenceLevel,
    delta = delta,
    gamma = gamma,
    vega = vega,
    theta = theta,
    rho = rho,
    pvValue = pvValue,
    jobCount = jobCount,
    completedCount = completedCount,
    failedCount = failedCount,
    runningCount = runningCount,
)

fun ChartDataSummary.toResponse(): ChartDataGatewayResponse = ChartDataGatewayResponse(
    points = points.map { it.toResponse() },
    bucketSizeMs = bucketSizeMs,
)

// --- Job History mappers ---

fun JobPhaseItem.toDto(): JobPhaseDto = JobPhaseDto(
    name = name,
    status = status,
    startedAt = startedAt.toString(),
    completedAt = completedAt?.toString(),
    durationMs = durationMs,
    details = details,
    error = error,
)

fun ValuationJobSummaryItem.toResponse(): ValuationJobSummaryResponse = ValuationJobSummaryResponse(
    jobId = jobId,
    bookId = bookId,
    triggerType = triggerType,
    status = status,
    startedAt = startedAt.toString(),
    completedAt = completedAt?.toString(),
    durationMs = durationMs,
    calculationType = calculationType,
    confidenceLevel = confidenceLevel,
    varValue = varValue,
    expectedShortfall = expectedShortfall,
    pvValue = pvValue,
    delta = delta,
    gamma = gamma,
    vega = vega,
    theta = theta,
    rho = rho,
    valuationDate = valuationDate,
    runLabel = runLabel,
    promotedAt = promotedAt,
    promotedBy = promotedBy,
    currentPhase = currentPhase,
    manifestId = manifestId,
)

fun ValuationJobDetailItem.toResponse(): ValuationJobDetailResponse = ValuationJobDetailResponse(
    jobId = jobId,
    bookId = bookId,
    triggerType = triggerType,
    status = status,
    startedAt = startedAt.toString(),
    completedAt = completedAt?.toString(),
    durationMs = durationMs,
    calculationType = calculationType,
    confidenceLevel = confidenceLevel,
    varValue = varValue,
    expectedShortfall = expectedShortfall,
    pvValue = pvValue,
    phases = phases.map { it.toDto() },
    error = error,
    valuationDate = valuationDate,
    runLabel = runLabel,
    promotedAt = promotedAt,
    promotedBy = promotedBy,
    currentPhase = currentPhase,
    manifestId = manifestId,
)

// --- Stress Scenario Governance DTOs ---

@Serializable
data class StressScenarioResponse(
    val id: String,
    val name: String,
    val description: String,
    val shocks: String,
    val status: String,
    val createdBy: String,
    val approvedBy: String?,
    val approvedAt: String?,
    val createdAt: String,
    val scenarioType: String = "PARAMETRIC",
)

@Serializable
data class CreateScenarioRequest(
    val name: String,
    val description: String,
    val shocks: String,
    val createdBy: String,
)

@Serializable
data class ApproveScenarioRequest(
    val approvedBy: String,
)

// --- Stress Scenario mappers ---

fun StressScenarioItem.toResponse(): StressScenarioResponse = StressScenarioResponse(
    id = id,
    name = name,
    description = description,
    shocks = shocks,
    status = status,
    createdBy = createdBy,
    approvedBy = approvedBy,
    approvedAt = approvedAt,
    createdAt = createdAt,
    scenarioType = scenarioType,
)

// --- Historical Replay DTOs ---

@Serializable
data class InstrumentDailyReturnsRequest(
    val instrumentId: String,
    val dailyReturns: List<Double>,
)

@Serializable
data class HistoricalReplayRequest(
    val instrumentReturns: List<InstrumentDailyReturnsRequest> = emptyList(),
    val scenarioName: String? = null,
    val windowStart: String? = null,
    val windowEnd: String? = null,
)

@Serializable
data class PositionReplayImpactDto(
    val instrumentId: String,
    val assetClass: String,
    val marketValue: String,
    val pnlImpact: String,
    val dailyPnl: List<String>,
    val proxyUsed: Boolean,
)

@Serializable
data class HistoricalReplayResponse(
    val scenarioName: String,
    val totalPnlImpact: String,
    val positionImpacts: List<PositionReplayImpactDto>,
    val windowStart: String?,
    val windowEnd: String?,
    val calculatedAt: String,
)

// --- Reverse Stress DTOs ---

@Serializable
data class ReverseStressRequest(
    val targetLoss: Double,
    val maxShock: Double = -1.0,
)

@Serializable
data class InstrumentShockDto(
    val instrumentId: String,
    val shock: String,
)

@Serializable
data class ReverseStressResponse(
    val shocks: List<InstrumentShockDto>,
    val achievedLoss: String,
    val targetLoss: String,
    val converged: Boolean,
    val calculatedAt: String,
)

// --- Historical Replay mappers ---

fun HistoricalReplayRequest.toParams(bookId: String): HistoricalReplayParams = HistoricalReplayParams(
    bookId = bookId,
    instrumentReturns = instrumentReturns.map {
        InstrumentDailyReturnsParam(instrumentId = it.instrumentId, dailyReturns = it.dailyReturns)
    },
    scenarioName = scenarioName,
    windowStart = windowStart,
    windowEnd = windowEnd,
)

fun PositionReplayImpactSummary.toDto(): PositionReplayImpactDto = PositionReplayImpactDto(
    instrumentId = instrumentId,
    assetClass = assetClass,
    marketValue = marketValue,
    pnlImpact = pnlImpact,
    dailyPnl = dailyPnl,
    proxyUsed = proxyUsed,
)

fun HistoricalReplayResultSummary.toResponse(): HistoricalReplayResponse = HistoricalReplayResponse(
    scenarioName = scenarioName,
    totalPnlImpact = totalPnlImpact,
    positionImpacts = positionImpacts.map { it.toDto() },
    windowStart = windowStart,
    windowEnd = windowEnd,
    calculatedAt = calculatedAt,
)

// --- Reverse Stress mappers ---

fun ReverseStressRequest.toParams(bookId: String): ReverseStressParams = ReverseStressParams(
    bookId = bookId,
    targetLoss = targetLoss,
    maxShock = maxShock,
)

fun InstrumentShockSummary.toDto(): InstrumentShockDto = InstrumentShockDto(
    instrumentId = instrumentId,
    shock = shock,
)

fun ReverseStressResultSummary.toResponse(): ReverseStressResponse = ReverseStressResponse(
    shocks = shocks.map { it.toDto() },
    achievedLoss = achievedLoss,
    targetLoss = targetLoss,
    converged = converged,
    calculatedAt = calculatedAt,
)
