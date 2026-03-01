"""Monte Carlo VaR with antithetic variates for variance reduction.

Antithetic variates: for each random draw Z, also compute the portfolio
return using -Z. Averaging the paired results cancels first-order error
terms and roughly halves the variance of the estimate.
"""

import numpy as np

from kinetix_risk.expected_shortfall import calculate_expected_shortfall
from kinetix_risk.models import AssetClassExposure, ComponentBreakdown, ConfidenceLevel, VaRResult

TRADING_DAYS_PER_YEAR = 252


def calculate_monte_carlo_var_antithetic(
    exposures: list[AssetClassExposure],
    confidence_level: ConfidenceLevel,
    time_horizon_days: int,
    correlation_matrix: np.ndarray,
    num_simulations: int = 10_000,
    seed: int | None = None,
) -> VaRResult:
    n = len(exposures)
    market_values = np.array([e.total_market_value for e in exposures])
    daily_vols = np.array([e.volatility / np.sqrt(TRADING_DAYS_PER_YEAR) for e in exposures])

    rng = np.random.default_rng(seed)

    # Cholesky decomposition for correlated draws
    cholesky = np.linalg.cholesky(correlation_matrix)
    z = rng.standard_normal((num_simulations, n))

    # Original paths
    correlated_returns = z @ cholesky.T * daily_vols
    portfolio_losses_orig = -(correlated_returns @ market_values)

    # Antithetic paths (using -Z)
    correlated_returns_anti = -z @ cholesky.T * daily_vols
    portfolio_losses_anti = -(correlated_returns_anti @ market_values)

    # Combine both sets of paths (2N total paths) for quantile estimation.
    # The variance reduction comes from the negative correlation between
    # paired paths: the estimator variance is Var(L) + Cov(L, L') which
    # is smaller than 2*Var(L) when the covariance is negative.
    portfolio_losses = np.concatenate([portfolio_losses_orig, portfolio_losses_anti])

    # 1-day VaR at confidence level
    alpha = confidence_level.value
    var_1d = float(np.percentile(portfolio_losses, alpha * 100))
    var_value = var_1d * np.sqrt(time_horizon_days)

    # Expected shortfall from the combined distribution
    es_1d = calculate_expected_shortfall(portfolio_losses, confidence_level)
    es_value = es_1d * np.sqrt(time_horizon_days)

    # Component breakdown using combined individual asset losses
    individual_losses_orig = -(correlated_returns * market_values)
    individual_losses_anti = -(correlated_returns_anti * market_values)
    individual_losses = np.concatenate([individual_losses_orig, individual_losses_anti])

    component_var_1d = []
    for i in range(n):
        asset_losses = individual_losses[:, i]
        cv = float(np.percentile(asset_losses, alpha * 100))
        component_var_1d.append(cv)

    total_component = sum(component_var_1d) if sum(component_var_1d) > 0 else 1.0
    breakdown = []
    for i, exp in enumerate(exposures):
        cv = component_var_1d[i] * np.sqrt(time_horizon_days)
        pct = (component_var_1d[i] / total_component * 100) if total_component > 0 else 0.0
        breakdown.append(ComponentBreakdown(exp.asset_class, float(cv), float(pct)))

    return VaRResult(float(var_value), float(es_value), breakdown)
