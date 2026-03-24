package com.kinetix.risk.service

import com.kinetix.risk.model.AdaptiveVaRParameters
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.MarketRegime

/**
 * Provides VaR calculation parameters appropriate for the current market regime.
 *
 * The parameter mapping is:
 *   NORMAL:       PARAMETRIC, CL_95, 1-day, standard correlations
 *   ELEVATED_VOL: HISTORICAL, CL_99, 1-day, EWMA volatility
 *   CRISIS:       MONTE_CARLO, CL_99, 5-day, stressed correlations, 50K sims
 *   RECOVERY:     HISTORICAL, CL_975, 1-day, standard correlations
 */
interface RegimeParameterProvider {
    fun parametersFor(regime: MarketRegime): AdaptiveVaRParameters
}
