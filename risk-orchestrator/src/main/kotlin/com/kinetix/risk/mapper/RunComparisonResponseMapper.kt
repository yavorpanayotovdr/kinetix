package com.kinetix.risk.mapper

import com.kinetix.risk.model.ComponentDiff
import com.kinetix.risk.model.InputChangeSummary
import com.kinetix.risk.model.MarketDataInputChange
import com.kinetix.risk.model.ParameterDiff
import com.kinetix.risk.model.PortfolioDiff
import com.kinetix.risk.model.PositionDiff
import com.kinetix.risk.model.PositionInputChange
import com.kinetix.risk.model.PositionRisk
import com.kinetix.risk.model.RunComparison
import com.kinetix.risk.model.RunSnapshot
import com.kinetix.risk.model.VaRAttribution
import com.kinetix.risk.routes.dtos.ComponentBreakdownDto
import com.kinetix.risk.routes.dtos.ComponentDiffResponse
import com.kinetix.risk.routes.dtos.InputChangesSummaryDto
import com.kinetix.risk.routes.dtos.MarketDataInputChangeDto
import com.kinetix.risk.routes.dtos.ParameterDiffDto
import com.kinetix.risk.routes.dtos.PortfolioDiffResponse
import com.kinetix.risk.routes.dtos.PositionDiffResponse
import com.kinetix.risk.routes.dtos.PositionInputChangeDto
import com.kinetix.risk.routes.dtos.PositionRiskDto
import com.kinetix.risk.routes.dtos.RunComparisonResponse
import com.kinetix.risk.routes.dtos.RunSnapshotResponse
import com.kinetix.risk.routes.dtos.VaRAttributionResponse

fun RunComparison.toResponse(): RunComparisonResponse = RunComparisonResponse(
    comparisonId = comparisonId.toString(),
    comparisonType = type.name,
    portfolioId = portfolioId,
    baseRun = baseRun.toResponse(),
    targetRun = targetRun.toResponse(),
    portfolioDiff = portfolioDiff.toResponse(),
    componentDiffs = componentDiffs.map { it.toResponse() },
    positionDiffs = positionDiffs.map { it.toResponse() },
    parameterDiffs = parameterDiffs.map { it.toDto() },
    attribution = attribution?.toResponse(),
    inputChanges = inputChanges?.toDto(),
)

fun RunSnapshot.toResponse(): RunSnapshotResponse = RunSnapshotResponse(
    jobId = jobId?.toString(),
    label = label,
    valuationDate = valuationDate.toString(),
    calcType = calculationType?.name,
    confLevel = confidenceLevel?.name,
    varValue = varValue?.let { "%.2f".format(it) },
    es = expectedShortfall?.let { "%.2f".format(it) },
    pv = pvValue?.let { "%.2f".format(it) },
    delta = delta?.let { "%.6f".format(it) },
    gamma = gamma?.let { "%.6f".format(it) },
    vega = vega?.let { "%.6f".format(it) },
    theta = theta?.let { "%.6f".format(it) },
    rho = rho?.let { "%.6f".format(it) },
    componentBreakdown = componentBreakdowns.map {
        ComponentBreakdownDto(
            assetClass = it.assetClass.name,
            varContribution = "%.2f".format(it.varContribution),
            percentageOfTotal = "%.2f".format(it.percentageOfTotal),
        )
    },
    positionRisk = positionRisks.map { it.toPositionRiskDto() },
    modelVersion = modelVersion,
    parameters = parameters,
    calculatedAt = calculatedAt?.toString(),
)

fun PortfolioDiff.toResponse(): PortfolioDiffResponse = PortfolioDiffResponse(
    varChange = "%.2f".format(varChange),
    varChangePercent = varChangePercent?.let { "%.2f".format(it) },
    esChange = "%.2f".format(esChange),
    esChangePercent = esChangePercent?.let { "%.2f".format(it) },
    pvChange = "%.2f".format(pvChange),
    deltaChange = "%.6f".format(deltaChange),
    gammaChange = "%.6f".format(gammaChange),
    vegaChange = "%.6f".format(vegaChange),
    thetaChange = "%.6f".format(thetaChange),
    rhoChange = "%.6f".format(rhoChange),
)

fun ComponentDiff.toResponse(): ComponentDiffResponse = ComponentDiffResponse(
    assetClass = assetClass.name,
    baseContribution = "%.2f".format(baseContribution),
    targetContribution = "%.2f".format(targetContribution),
    change = "%.2f".format(change),
    changePercent = changePercent?.let { "%.2f".format(it) },
)

fun PositionDiff.toResponse(): PositionDiffResponse = PositionDiffResponse(
    instrumentId = instrumentId.value,
    assetClass = assetClass.name,
    changeType = changeType.name,
    baseMarketValue = "%.2f".format(baseMarketValue),
    targetMarketValue = "%.2f".format(targetMarketValue),
    marketValueChange = "%.2f".format(marketValueChange),
    baseVarContribution = "%.2f".format(baseVarContribution),
    targetVarContribution = "%.2f".format(targetVarContribution),
    varContributionChange = "%.2f".format(varContributionChange),
    baseDelta = baseDelta?.let { "%.6f".format(it) },
    targetDelta = targetDelta?.let { "%.6f".format(it) },
    baseGamma = baseGamma?.let { "%.6f".format(it) },
    targetGamma = targetGamma?.let { "%.6f".format(it) },
    baseVega = baseVega?.let { "%.6f".format(it) },
    targetVega = targetVega?.let { "%.6f".format(it) },
)

fun ParameterDiff.toDto(): ParameterDiffDto = ParameterDiffDto(
    paramName = paramName,
    baseValue = baseValue,
    targetValue = targetValue,
)

fun VaRAttribution.toResponse(): VaRAttributionResponse = VaRAttributionResponse(
    totalChange = "%.2f".format(totalChange),
    positionEffect = "%.2f".format(positionEffect),
    volEffect = volEffect?.let { "%.2f".format(it) },
    corrEffect = corrEffect?.let { "%.2f".format(it) },
    timeDecayEffect = "%.2f".format(timeDecayEffect),
    unexplained = "%.2f".format(unexplained),
    effectMagnitudes = effectMagnitudes.mapValues { it.value.name },
    caveats = caveats,
)

fun InputChangeSummary.toDto(): InputChangesSummaryDto = InputChangesSummaryDto(
    positionsChanged = positionsChanged,
    marketDataChanged = marketDataChanged,
    modelVersionChanged = modelVersionChanged,
    baseModelVersion = baseModelVersion,
    targetModelVersion = targetModelVersion,
    positionChanges = positionChanges.map { it.toDto() },
    marketDataChanges = marketDataChanges.map { it.toDto() },
    baseManifestId = baseManifestId,
    targetManifestId = targetManifestId,
)

fun PositionInputChange.toDto(): PositionInputChangeDto = PositionInputChangeDto(
    instrumentId = instrumentId,
    assetClass = assetClass,
    changeType = changeType.name,
    baseQuantity = baseQuantity?.toPlainString(),
    targetQuantity = targetQuantity?.toPlainString(),
    quantityDelta = quantityDelta?.toPlainString(),
    baseMarketPrice = baseMarketPrice?.toPlainString(),
    targetMarketPrice = targetMarketPrice?.toPlainString(),
    priceDelta = priceDelta?.toPlainString(),
    currency = currency,
)

fun MarketDataInputChange.toDto(): MarketDataInputChangeDto = MarketDataInputChangeDto(
    dataType = dataType,
    instrumentId = instrumentId,
    assetClass = assetClass,
    changeType = changeType.name,
    magnitude = magnitude?.name,
)

private fun PositionRisk.toPositionRiskDto(): PositionRiskDto = PositionRiskDto(
    instrumentId = instrumentId.value,
    assetClass = assetClass.name,
    marketValue = marketValue.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
    delta = delta?.let { "%.6f".format(it) },
    gamma = gamma?.let { "%.6f".format(it) },
    vega = vega?.let { "%.6f".format(it) },
    varContribution = varContribution.toPlainString(),
    esContribution = esContribution.toPlainString(),
    percentageOfTotal = percentageOfTotal.toPlainString(),
)
