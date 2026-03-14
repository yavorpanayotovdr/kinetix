package com.kinetix.risk.service

import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.client.PositionProvider
import com.kinetix.risk.client.RiskEngineClient
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ChangeMagnitude
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.InputChangeSummary
import com.kinetix.risk.model.VaRAttribution
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.model.ValuationJob
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class VaRAttributionService(
    private val riskEngineClient: RiskEngineClient,
    private val positionProvider: PositionProvider,
) {
    suspend fun attributeVaRChange(
        portfolioId: PortfolioId,
        baseJob: ValuationJob,
        targetJob: ValuationJob,
        inputChanges: InputChangeSummary? = null,
    ): VaRAttribution {
        val baseVaR = baseJob.varValue ?: 0.0
        val targetVaR = targetJob.varValue ?: 0.0
        val totalChange = targetVaR - baseVaR

        if (totalChange == 0.0) {
            return VaRAttribution(
                totalChange = 0.0,
                positionEffect = 0.0,
                volEffect = 0.0,
                corrEffect = 0.0,
                modelEffect = 0.0,
                timeDecayEffect = 0.0,
                unexplained = 0.0,
            )
        }

        // Step 1: Fetch current (target-date) positions
        val currentPositions = positionProvider.getPositions(portfolioId)

        // Step 2: Position effect — re-run with current positions under base calc parameters.
        val calcType = baseJob.calculationType
            ?.let { runCatching { CalculationType.valueOf(it) }.getOrNull() }
            ?: CalculationType.PARAMETRIC
        val confLevel = baseJob.confidenceLevel
            ?.let { runCatching { ConfidenceLevel.valueOf(it) }.getOrNull() }
            ?: ConfidenceLevel.CL_95

        val request = VaRCalculationRequest(
            portfolioId = portfolioId,
            calculationType = calcType,
            confidenceLevel = confLevel,
        )

        val revaluedWithCurrentPositions = riskEngineClient.valuate(request, currentPositions)
        val positionEffect = (revaluedWithCurrentPositions.varValue ?: 0.0) - baseVaR

        // Step 3: Time decay — theta * calendar days between valuation dates.
        val daysBetween = ChronoUnit.DAYS.between(baseJob.valuationDate, targetJob.valuationDate)
            .toDouble()
            .coerceAtLeast(1.0)
        val theta = baseJob.theta ?: 0.0
        val timeDecayEffect = theta * daysBetween

        // Step 4: First-order sensitivity-based vol/corr attribution
        val volEffect = estimateVolEffect(baseJob, inputChanges)
        val corrEffect: Double? = null // Requires correlation matrix diff — deferred
        val modelEffect = if (inputChanges?.modelVersionChanged == true) {
            // If model version changed, attribute some of the unexplained to model
            null // Can't decompose further without re-run
        } else null

        val explained = positionEffect + timeDecayEffect + (volEffect ?: 0.0) + (corrEffect ?: 0.0)
        val unexplained = totalChange - explained

        // Step 5: Magnitude classification for each effect
        val pvValue = baseJob.pvValue ?: 0.0
        val varFloor = abs(pvValue) * 0.005 // 0.5% of portfolio value
        val denominator = maxOf(abs(totalChange), varFloor).coerceAtLeast(1.0) // avoid division by zero

        val effectMagnitudes = buildMap {
            put("position", classifyEffectMagnitude(positionEffect, denominator))
            put("timeDecay", classifyEffectMagnitude(timeDecayEffect, denominator))
            if (volEffect != null) put("vol", classifyEffectMagnitude(volEffect, denominator))
            if (corrEffect != null) put("corr", classifyEffectMagnitude(corrEffect, denominator))
            put("unexplained", classifyEffectMagnitude(unexplained, denominator))
        }

        // Step 6: Diagnostic caveats
        val caveats = buildCaveats(baseJob, targetJob, inputChanges, positionEffect, volEffect)

        return VaRAttribution(
            totalChange = totalChange,
            positionEffect = positionEffect,
            volEffect = volEffect,
            corrEffect = corrEffect,
            modelEffect = modelEffect,
            timeDecayEffect = timeDecayEffect,
            unexplained = unexplained,
            effectMagnitudes = effectMagnitudes,
            caveats = caveats,
        )
    }

    private fun estimateVolEffect(baseJob: ValuationJob, inputChanges: InputChangeSummary?): Double? {
        val vega = baseJob.vega ?: return null
        if (inputChanges == null || !inputChanges.marketDataChanged) return 0.0

        // Look for vol surface or spot price changes that have magnitude info
        val volChanges = inputChanges.marketDataChanges.filter {
            it.dataType == "VOLATILITY_SURFACE" && it.magnitude != null
        }
        if (volChanges.isEmpty()) return null

        // Approximate: use magnitude to estimate a percentage vol shift
        // LARGE ~10%, MEDIUM ~3%, SMALL ~0.5%
        val avgVolShift = volChanges.map { md ->
            when (md.magnitude) {
                ChangeMagnitude.LARGE -> 0.10
                ChangeMagnitude.MEDIUM -> 0.03
                ChangeMagnitude.SMALL -> 0.005
                null -> 0.0
            }
        }.average()

        // First-order: volEffect ≈ vega * avg_vol_shift_in_points
        return vega * avgVolShift
    }

    private fun classifyEffectMagnitude(effect: Double, denominator: Double): ChangeMagnitude {
        val share = abs(effect) / denominator
        return when {
            share > 0.40 -> ChangeMagnitude.LARGE
            share > 0.15 -> ChangeMagnitude.MEDIUM
            else -> ChangeMagnitude.SMALL
        }
    }

    private fun buildCaveats(
        baseJob: ValuationJob,
        targetJob: ValuationJob,
        inputChanges: InputChangeSummary?,
        positionEffect: Double,
        volEffect: Double?,
    ): List<String> = buildList {
        // Large gamma positions
        val gamma = baseJob.gamma ?: 0.0
        val delta = baseJob.delta ?: 0.0
        if (inputChanges != null && delta != 0.0) {
            val spotChanges = inputChanges.positionChanges.mapNotNull { pc ->
                if (pc.baseMarketPrice != null && pc.priceDelta != null) {
                    pc.priceDelta.toDouble() / pc.baseMarketPrice.toDouble()
                } else null
            }
            if (spotChanges.isNotEmpty()) {
                val avgSpotChange = spotChanges.average()
                val secondOrder = abs(gamma * avgSpotChange * avgSpotChange)
                val firstOrder = abs(delta * avgSpotChange)
                if (firstOrder > 0 && secondOrder > 0.1 * firstOrder) {
                    add("Large gamma positions detected; first-order attribution may understate second-order effects")
                }
            }
        }

        // Different Monte Carlo seeds
        val baseCalc = baseJob.calculationType
        val targetCalc = targetJob.calculationType
        if (baseCalc == "MONTE_CARLO" || targetCalc == "MONTE_CARLO") {
            add("Monte Carlo simulation: sampling variance may contribute to observed differences")
        }

        // Historical VaR
        if (baseCalc == "HISTORICAL" || targetCalc == "HISTORICAL") {
            add("Historical VaR is not a differentiable function of inputs; attribution is scenario-based, not sensitivity-based")
        }

        // Model version changed
        if (inputChanges?.modelVersionChanged == true) {
            add("Model version changed between runs (${inputChanges.baseModelVersion} → ${inputChanges.targetModelVersion}); model effect cannot be decomposed further")
        }

        // Vol effect is approximated
        if (volEffect != null && volEffect != 0.0) {
            add("Volatility effect is a first-order estimate using portfolio vega and average magnitude; actual attribution requires full re-pricing")
        }
    }
}
