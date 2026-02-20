from collections import defaultdict

from kinetix_risk.models import (
    AssetClass, AssetClassExposure, CalculationType, ConfidenceLevel,
    PositionRisk, VaRResult,
)
from kinetix_risk.var_historical import calculate_historical_var
from kinetix_risk.var_monte_carlo import calculate_monte_carlo_var
from kinetix_risk.var_parametric import calculate_parametric_var
from kinetix_risk.volatility import get_sub_correlation_matrix, get_volatility


def calculate_portfolio_var(
    positions: list[PositionRisk],
    calculation_type: CalculationType,
    confidence_level: ConfidenceLevel,
    time_horizon_days: int,
    num_simulations: int = 10_000,
) -> VaRResult:
    if not positions:
        raise ValueError("Cannot calculate VaR on empty positions list")

    # Group positions by asset class and sum market values
    grouped: dict[AssetClass, float] = defaultdict(float)
    for pos in positions:
        grouped[pos.asset_class] += pos.market_value

    # Build exposures with volatility assumptions
    asset_classes = sorted(grouped.keys(), key=lambda ac: ac.value)
    exposures = [
        AssetClassExposure(ac, grouped[ac], get_volatility(ac))
        for ac in asset_classes
    ]

    # Get correlation sub-matrix for the asset classes present
    corr = get_sub_correlation_matrix(asset_classes)

    # Dispatch to the appropriate VaR engine
    if calculation_type == CalculationType.PARAMETRIC:
        return calculate_parametric_var(exposures, confidence_level, time_horizon_days, corr)
    elif calculation_type == CalculationType.HISTORICAL:
        return calculate_historical_var(exposures, confidence_level, time_horizon_days, corr)
    elif calculation_type == CalculationType.MONTE_CARLO:
        return calculate_monte_carlo_var(
            exposures, confidence_level, time_horizon_days, corr,
            num_simulations=num_simulations,
        )
    else:
        raise ValueError(f"Unknown calculation type: {calculation_type}")
