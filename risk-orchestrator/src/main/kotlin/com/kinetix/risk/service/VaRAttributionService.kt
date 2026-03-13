package com.kinetix.risk.service

import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.client.PositionProvider
import com.kinetix.risk.client.RiskEngineClient
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.VaRAttribution
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.model.ValuationJob
import java.time.temporal.ChronoUnit

class VaRAttributionService(
    private val riskEngineClient: RiskEngineClient,
    private val positionProvider: PositionProvider,
) {
    suspend fun attributeVaRChange(
        portfolioId: PortfolioId,
        baseJob: ValuationJob,
        targetJob: ValuationJob,
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
        // This isolates how much of the VaR change is purely due to position changes.
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

        // Step 3: Time decay — approximated as theta * calendar days between valuation dates.
        // Theta is already expressed as a daily dollar decay, so one day of elapsed time
        // contributes exactly theta to the VaR change.
        val daysBetween = ChronoUnit.DAYS.between(baseJob.valuationDate, targetJob.valuationDate)
            .toDouble()
            .coerceAtLeast(1.0)
        val theta = baseJob.theta ?: 0.0
        val timeDecayEffect = theta * daysBetween

        // Steps 4–5: Vol and correlation effects require market-data overrides that go
        // beyond the current MarketDataFetcher surface. Deferred to a future increment;
        // their contribution is absorbed into unexplained for now.
        val volEffect = 0.0
        val corrEffect = 0.0
        val modelEffect = 0.0

        val unexplained = totalChange - positionEffect - volEffect - corrEffect - timeDecayEffect - modelEffect

        return VaRAttribution(
            totalChange = totalChange,
            positionEffect = positionEffect,
            volEffect = volEffect,
            corrEffect = corrEffect,
            modelEffect = modelEffect,
            timeDecayEffect = timeDecayEffect,
            unexplained = unexplained,
        )
    }
}
