package com.kinetix.gateway.dto

import com.kinetix.common.model.*
import com.kinetix.gateway.client.BookTradeCommand
import com.kinetix.gateway.client.BookTradeResult
import com.kinetix.gateway.client.AssetClassImpactItem
import com.kinetix.gateway.client.ComponentBreakdownItem
import com.kinetix.gateway.client.FrtbResultSummary
import com.kinetix.gateway.client.GreekValuesItem
import com.kinetix.gateway.client.GreeksResultSummary
import com.kinetix.gateway.client.PortfolioSummary
import com.kinetix.gateway.client.ReportResult
import com.kinetix.gateway.client.RiskClassChargeItem
import com.kinetix.gateway.client.StressTestParams
import com.kinetix.gateway.client.StressTestResultSummary
import com.kinetix.gateway.client.VaRCalculationParams
import com.kinetix.gateway.client.VaRResultSummary
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
    val portfolioId: String,
    val instrumentId: String,
    val assetClass: String,
    val side: String,
    val quantity: String,
    val price: MoneyDto,
    val tradedAt: String,
)

@Serializable
data class PositionResponse(
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
data class BookTradeResponse(
    val trade: TradeResponse,
    val position: PositionResponse,
)

@Serializable
data class PortfolioSummaryResponse(
    val portfolioId: String,
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
)

// --- Domain -> DTO mappers ---

fun Money.toDto(): MoneyDto = MoneyDto(
    amount = amount.toPlainString(),
    currency = currency.currencyCode,
)

fun Trade.toResponse(): TradeResponse = TradeResponse(
    tradeId = tradeId.value,
    portfolioId = portfolioId.value,
    instrumentId = instrumentId.value,
    assetClass = assetClass.name,
    side = side.name,
    quantity = quantity.toPlainString(),
    price = price.toDto(),
    tradedAt = tradedAt.toString(),
)

fun Position.toResponse(): PositionResponse = PositionResponse(
    portfolioId = portfolioId.value,
    instrumentId = instrumentId.value,
    assetClass = assetClass.name,
    quantity = quantity.toPlainString(),
    averageCost = averageCost.toDto(),
    marketPrice = marketPrice.toDto(),
    marketValue = marketValue.toDto(),
    unrealizedPnl = unrealizedPnl.toDto(),
)

fun BookTradeResult.toResponse(): BookTradeResponse = BookTradeResponse(
    trade = trade.toResponse(),
    position = position.toResponse(),
)

fun PortfolioSummary.toResponse(): PortfolioSummaryResponse = PortfolioSummaryResponse(
    portfolioId = id.value,
)

// --- DTO -> Domain mappers ---

fun BookTradeRequest.toCommand(portfolioId: PortfolioId): BookTradeCommand {
    val qty = BigDecimal(quantity)
    require(qty > BigDecimal.ZERO) { "Trade quantity must be positive, was $qty" }
    val priceAmt = BigDecimal(priceAmount)
    require(priceAmt >= BigDecimal.ZERO) { "Trade price must be non-negative, was $priceAmt" }
    return BookTradeCommand(
        tradeId = TradeId(tradeId),
        portfolioId = portfolioId,
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
)

@Serializable
data class ComponentBreakdownDto(
    val assetClass: String,
    val varContribution: String,
    val percentageOfTotal: String,
)

@Serializable
data class VaRResultResponse(
    val portfolioId: String,
    val calculationType: String,
    val confidenceLevel: String,
    val varValue: String,
    val expectedShortfall: String,
    val componentBreakdown: List<ComponentBreakdownDto>,
    val calculatedAt: String,
)

// --- VaR mappers ---

private val validCalculationTypes = setOf("HISTORICAL", "PARAMETRIC", "MONTE_CARLO")
private val validConfidenceLevels = setOf("CL_95", "CL_99")

fun VaRCalculationRequest.toParams(portfolioId: String): VaRCalculationParams {
    val calcType = calculationType ?: "PARAMETRIC"
    require(calcType in validCalculationTypes) {
        "Invalid calculationType: $calcType. Must be one of $validCalculationTypes"
    }
    val confLevel = confidenceLevel ?: "CL_95"
    require(confLevel in validConfidenceLevels) {
        "Invalid confidenceLevel: $confLevel. Must be one of $validConfidenceLevels"
    }
    return VaRCalculationParams(
        portfolioId = portfolioId,
        calculationType = calcType,
        confidenceLevel = confLevel,
        timeHorizonDays = timeHorizonDays?.toInt() ?: 1,
        numSimulations = numSimulations?.toInt() ?: 10_000,
    )
}

fun ComponentBreakdownItem.toDto(): ComponentBreakdownDto = ComponentBreakdownDto(
    assetClass = assetClass,
    varContribution = "%.2f".format(varContribution),
    percentageOfTotal = "%.2f".format(percentageOfTotal),
)

fun VaRResultSummary.toResponse(): VaRResultResponse = VaRResultResponse(
    portfolioId = portfolioId,
    calculationType = calculationType,
    confidenceLevel = confidenceLevel,
    varValue = "%.2f".format(varValue),
    expectedShortfall = "%.2f".format(expectedShortfall),
    componentBreakdown = componentBreakdown.map { it.toDto() },
    calculatedAt = calculatedAt.toString(),
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
data class StressTestResponse(
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
data class GreeksResponse(
    val portfolioId: String,
    val assetClassGreeks: List<GreekValuesDto>,
    val theta: String,
    val rho: String,
    val calculatedAt: String,
)

// --- Stress Test mappers ---

fun StressTestRequest.toParams(portfolioId: String): StressTestParams {
    val calcType = calculationType ?: "PARAMETRIC"
    require(calcType in validCalculationTypes) {
        "Invalid calculationType: $calcType. Must be one of $validCalculationTypes"
    }
    val confLevel = confidenceLevel ?: "CL_95"
    require(confLevel in validConfidenceLevels) {
        "Invalid confidenceLevel: $confLevel. Must be one of $validConfidenceLevels"
    }
    return StressTestParams(
        portfolioId = portfolioId,
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

fun StressTestResultSummary.toResponse(): StressTestResponse = StressTestResponse(
    scenarioName = scenarioName,
    baseVar = "%.2f".format(baseVar),
    stressedVar = "%.2f".format(stressedVar),
    pnlImpact = "%.2f".format(pnlImpact),
    assetClassImpacts = assetClassImpacts.map { it.toDto() },
    calculatedAt = calculatedAt.toString(),
)

fun GreekValuesItem.toDto(): GreekValuesDto = GreekValuesDto(
    assetClass = assetClass,
    delta = "%.6f".format(delta),
    gamma = "%.6f".format(gamma),
    vega = "%.6f".format(vega),
)

fun GreeksResultSummary.toResponse(): GreeksResponse = GreeksResponse(
    portfolioId = portfolioId,
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
data class GenerateReportRequest(
    val format: String? = null,
)

@Serializable
data class ReportResponse(
    val portfolioId: String,
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
    portfolioId = portfolioId,
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
    portfolioId = portfolioId,
    format = format,
    content = content,
    generatedAt = generatedAt.toString(),
)
