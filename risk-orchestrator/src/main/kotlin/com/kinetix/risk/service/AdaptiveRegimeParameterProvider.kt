package com.kinetix.risk.service

import com.kinetix.risk.model.AdaptiveVaRParameters
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.MarketRegime

/**
 * Maps market regimes to their corresponding adaptive VaR parameters per the spec:
 *
 *   NORMAL:       PARAMETRIC, CL_95, 1-day, standard correlations
 *   ELEVATED_VOL: HISTORICAL, CL_99, 1-day, EWMA volatility
 *   CRISIS:       MONTE_CARLO, CL_99, 5-day, stressed correlations, 50K sims
 *   RECOVERY:     HISTORICAL, CL_975, 1-day, standard correlations
 */
class AdaptiveRegimeParameterProvider : RegimeParameterProvider {

    override fun parametersFor(regime: MarketRegime): AdaptiveVaRParameters = when (regime) {
        MarketRegime.NORMAL -> AdaptiveVaRParameters(
            calculationType = CalculationType.PARAMETRIC,
            confidenceLevel = ConfidenceLevel.CL_95,
            timeHorizonDays = 1,
            correlationMethod = "standard",
            numSimulations = null,
        )
        MarketRegime.ELEVATED_VOL -> AdaptiveVaRParameters(
            calculationType = CalculationType.HISTORICAL,
            confidenceLevel = ConfidenceLevel.CL_99,
            timeHorizonDays = 1,
            correlationMethod = "ewma",
            numSimulations = null,
        )
        MarketRegime.CRISIS -> AdaptiveVaRParameters(
            calculationType = CalculationType.MONTE_CARLO,
            confidenceLevel = ConfidenceLevel.CL_99,
            timeHorizonDays = 5,
            correlationMethod = "stressed",
            numSimulations = 50_000,
        )
        MarketRegime.RECOVERY -> AdaptiveVaRParameters(
            calculationType = CalculationType.HISTORICAL,
            confidenceLevel = ConfidenceLevel.CL_975,
            timeHorizonDays = 1,
            correlationMethod = "standard",
            numSimulations = null,
        )
    }
}
