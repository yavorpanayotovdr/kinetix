import numpy as np
from scipy.stats import norm

from kinetix_risk.models import AssetClassExposure, ConfidenceLevel, ComponentBreakdown, VaRResult

TRADING_DAYS_PER_YEAR = 252


def calculate_parametric_var(
    exposures: list[AssetClassExposure],
    confidence_level: ConfidenceLevel,
    time_horizon_days: int,
    correlation_matrix: np.ndarray,
) -> VaRResult:
    n = len(exposures)
    market_values = np.array([e.total_market_value for e in exposures])
    daily_vols = np.array([e.volatility / np.sqrt(TRADING_DAYS_PER_YEAR) for e in exposures])

    # Dollar-weighted volatilities
    dollar_vols = daily_vols * market_values

    # Covariance matrix in dollar terms
    cov_matrix = np.outer(dollar_vols, dollar_vols) * correlation_matrix

    # Portfolio standard deviation
    ones = np.ones(n)
    port_variance = float(ones @ cov_matrix @ ones)
    port_std = np.sqrt(port_variance)

    alpha = confidence_level.value
    z = norm.ppf(alpha)
    sqrt_t = np.sqrt(time_horizon_days)

    var_value = z * port_std * sqrt_t

    # Analytical ES for normal distribution: ES = sigma * phi(z) / (1 - alpha)
    es_value = port_std * norm.pdf(z) / (1 - alpha) * sqrt_t

    # Component VaR via Euler allocation: CVaR_i = (Cov @ 1)_i * z / port_std
    marginal = cov_matrix @ ones
    component_var = marginal * z / port_std * sqrt_t

    breakdown = []
    for i, exp in enumerate(exposures):
        pct = (component_var[i] / var_value * 100) if var_value > 0 else 0.0
        breakdown.append(ComponentBreakdown(exp.asset_class, float(component_var[i]), float(pct)))

    return VaRResult(float(var_value), float(es_value), breakdown)
