import math
from collections import defaultdict

import numpy as np

from kinetix_risk.metrics import (
    risk_var_calculation_duration_seconds,
    risk_var_calculation_total,
)
from kinetix_risk.models import (
    AssetClass, AssetClassExposure, CalculationType, ConfidenceLevel,
    PositionRisk, VaRResult,
)
from kinetix_risk.var_historical import calculate_historical_var
from kinetix_risk.var_monte_carlo import calculate_monte_carlo_var
from kinetix_risk.var_parametric import calculate_parametric_var
from kinetix_risk.volatility import VolatilityProvider, get_sub_correlation_matrix


@risk_var_calculation_duration_seconds.time()
def calculate_portfolio_var(
    positions: list[PositionRisk],
    calculation_type: CalculationType,
    confidence_level: ConfidenceLevel,
    time_horizon_days: int,
    num_simulations: int = 10_000,
    volatility_provider: VolatilityProvider | None = None,
    correlation_matrix: "np.ndarray | None" = None,
    risk_free_rate: float = 0.0,
    historical_returns: "np.ndarray | None" = None,
) -> VaRResult:
    if not positions:
        raise ValueError("Cannot calculate VaR on empty positions list")

    risk_var_calculation_total.labels(
        calculation_type=calculation_type.value,
        confidence_level=str(confidence_level.value),
    ).inc()

    # Group positions by asset class and sum market values
    grouped: dict[AssetClass, float] = defaultdict(float)
    for pos in positions:
        grouped[pos.asset_class] += pos.market_value

    # Discount grouped market values when a risk-free rate is provided
    if risk_free_rate != 0.0:
        discount = math.exp(-risk_free_rate * time_horizon_days / 252)
        grouped = {ac: mv * discount for ac, mv in grouped.items()}

    # Build exposures with volatility assumptions
    vol_fn = volatility_provider or VolatilityProvider.static()
    asset_classes = sorted(grouped.keys(), key=lambda ac: ac.value)
    exposures = [
        AssetClassExposure(ac, grouped[ac], vol_fn(ac))
        for ac in asset_classes
    ]

    # Get correlation sub-matrix for the asset classes present
    corr = correlation_matrix if correlation_matrix is not None else get_sub_correlation_matrix(asset_classes)

    # Dispatch to the appropriate VaR engine
    if calculation_type == CalculationType.PARAMETRIC:
        return calculate_parametric_var(exposures, confidence_level, time_horizon_days, corr)
    elif calculation_type == CalculationType.HISTORICAL:
        return calculate_historical_var(
            exposures, confidence_level, time_horizon_days, corr,
            historical_returns=historical_returns,
        )
    elif calculation_type == CalculationType.MONTE_CARLO:
        return calculate_monte_carlo_var(
            exposures, confidence_level, time_horizon_days, corr,
            num_simulations=num_simulations,
        )
    else:
        raise ValueError(f"Unknown calculation type: {calculation_type}")
