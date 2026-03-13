package com.kinetix.risk.service

import com.kinetix.risk.model.ComponentBreakdown
import com.kinetix.risk.model.ComponentDiff
import com.kinetix.risk.model.ParameterDiff
import com.kinetix.risk.model.PortfolioDiff
import com.kinetix.risk.model.PositionChangeType
import com.kinetix.risk.model.PositionDiff
import com.kinetix.risk.model.PositionRisk
import com.kinetix.risk.model.RunSnapshot
import java.math.BigDecimal

class SnapshotDiffer {

    fun computePortfolioDiff(base: RunSnapshot, target: RunSnapshot): PortfolioDiff {
        val varChange = (target.varValue ?: 0.0) - (base.varValue ?: 0.0)
        val varChangePercent = percentChange(base.varValue, varChange)

        val esChange = (target.expectedShortfall ?: 0.0) - (base.expectedShortfall ?: 0.0)
        val esChangePercent = percentChange(base.expectedShortfall, esChange)

        return PortfolioDiff(
            varChange = varChange,
            varChangePercent = varChangePercent,
            esChange = esChange,
            esChangePercent = esChangePercent,
            pvChange = (target.pvValue ?: 0.0) - (base.pvValue ?: 0.0),
            deltaChange = (target.delta ?: 0.0) - (base.delta ?: 0.0),
            gammaChange = (target.gamma ?: 0.0) - (base.gamma ?: 0.0),
            vegaChange = (target.vega ?: 0.0) - (base.vega ?: 0.0),
            thetaChange = (target.theta ?: 0.0) - (base.theta ?: 0.0),
            rhoChange = (target.rho ?: 0.0) - (base.rho ?: 0.0),
        )
    }

    fun matchPositions(base: List<PositionRisk>, target: List<PositionRisk>): List<PositionDiff> {
        val baseByInstrument = base.associateBy { it.instrumentId }
        val targetByInstrument = target.associateBy { it.instrumentId }
        val allIds = baseByInstrument.keys + targetByInstrument.keys

        return allIds.map { instrumentId ->
            val b = baseByInstrument[instrumentId]
            val t = targetByInstrument[instrumentId]

            when {
                b == null -> positionDiff(instrumentId.value, null, t!!, PositionChangeType.NEW)
                t == null -> positionDiff(instrumentId.value, b, null, PositionChangeType.REMOVED)
                positionsMatch(b, t) -> positionDiff(instrumentId.value, b, t, PositionChangeType.UNCHANGED)
                else -> positionDiff(instrumentId.value, b, t, PositionChangeType.MODIFIED)
            }
        }
    }

    fun matchComponents(
        base: List<ComponentBreakdown>,
        target: List<ComponentBreakdown>,
    ): List<ComponentDiff> {
        val baseByClass = base.associateBy { it.assetClass }
        val targetByClass = target.associateBy { it.assetClass }
        val allClasses = (baseByClass.keys + targetByClass.keys).distinct()

        return allClasses.map { assetClass ->
            val baseContrib = baseByClass[assetClass]?.varContribution ?: 0.0
            val targetContrib = targetByClass[assetClass]?.varContribution ?: 0.0
            val change = targetContrib - baseContrib
            ComponentDiff(
                assetClass = assetClass,
                baseContribution = baseContrib,
                targetContribution = targetContrib,
                change = change,
                changePercent = percentChange(baseContrib, change),
            )
        }
    }

    fun diffParameters(base: RunSnapshot, target: RunSnapshot): List<ParameterDiff> {
        val allKeys = (base.parameters.keys + target.parameters.keys).distinct()
        return allKeys
            .filter { key -> base.parameters[key] != target.parameters[key] }
            .map { key ->
                ParameterDiff(
                    paramName = key,
                    baseValue = base.parameters[key],
                    targetValue = target.parameters[key],
                )
            }
    }

    private fun percentChange(base: Double?, change: Double): Double? {
        if (base == null || base == 0.0) return null
        return change / base * 100.0
    }

    private fun positionsMatch(b: PositionRisk, t: PositionRisk): Boolean =
        b.marketValue.compareTo(t.marketValue) == 0 &&
            b.varContribution.compareTo(t.varContribution) == 0 &&
            b.esContribution.compareTo(t.esContribution) == 0 &&
            b.delta == t.delta &&
            b.gamma == t.gamma &&
            b.vega == t.vega

    private fun positionDiff(
        instrumentIdValue: String,
        base: PositionRisk?,
        target: PositionRisk?,
        changeType: PositionChangeType,
    ): PositionDiff {
        val assetClass = (base ?: target)!!.assetClass
        val baseMV = base?.marketValue ?: BigDecimal.ZERO
        val targetMV = target?.marketValue ?: BigDecimal.ZERO
        val baseVar = base?.varContribution ?: BigDecimal.ZERO
        val targetVar = target?.varContribution ?: BigDecimal.ZERO

        return PositionDiff(
            instrumentId = com.kinetix.common.model.InstrumentId(instrumentIdValue),
            assetClass = assetClass,
            changeType = changeType,
            baseMarketValue = baseMV,
            targetMarketValue = targetMV,
            marketValueChange = targetMV - baseMV,
            baseVarContribution = baseVar,
            targetVarContribution = targetVar,
            varContributionChange = targetVar - baseVar,
            baseDelta = base?.delta,
            targetDelta = target?.delta,
            baseGamma = base?.gamma,
            targetGamma = target?.gamma,
            baseVega = base?.vega,
            targetVega = target?.vega,
        )
    }
}
