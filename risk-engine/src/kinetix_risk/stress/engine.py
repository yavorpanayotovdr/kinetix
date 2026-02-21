from kinetix_risk.models import (
    AssetClass, AssetClassImpact, CalculationType, ConfidenceLevel,
    PositionRisk, StressScenario, StressTestResult,
)
from kinetix_risk.portfolio_risk import calculate_portfolio_var
from kinetix_risk.volatility import DEFAULT_VOLATILITIES, VolatilityProvider, get_sub_correlation_matrix


def run_stress_test(
    positions: list[PositionRisk],
    scenario: StressScenario,
    calculation_type: CalculationType = CalculationType.PARAMETRIC,
    confidence_level: ConfidenceLevel = ConfidenceLevel.CL_95,
    time_horizon_days: int = 1,
) -> StressTestResult:
    if not positions:
        raise ValueError("Cannot run stress test on empty positions list")

    # Compute base VaR with default vols/correlations
    base_result = calculate_portfolio_var(
        positions, calculation_type, confidence_level, time_horizon_days,
    )

    # Group positions by asset class for impact tracking
    base_exposures: dict[AssetClass, float] = {}
    for pos in positions:
        base_exposures[pos.asset_class] = base_exposures.get(pos.asset_class, 0.0) + pos.market_value

    # Apply price shocks to create stressed positions
    stressed_positions = []
    for pos in positions:
        price_shock = scenario.price_shocks.get(pos.asset_class, 1.0)
        stressed_positions.append(PositionRisk(
            instrument_id=pos.instrument_id,
            asset_class=pos.asset_class,
            market_value=pos.market_value * price_shock,
            currency=pos.currency,
        ))

    # Build stressed volatilities
    stressed_vols = {}
    for ac, base_vol in DEFAULT_VOLATILITIES.items():
        vol_shock = scenario.vol_shocks.get(ac, 1.0)
        stressed_vols[ac] = base_vol * vol_shock
    vol_provider = VolatilityProvider.from_dict(stressed_vols)

    # Build stressed correlation matrix
    asset_classes = sorted(base_exposures.keys(), key=lambda ac: ac.value)
    if scenario.correlation_override is not None:
        from kinetix_risk.volatility import _ASSET_CLASS_INDEX
        indices = [_ASSET_CLASS_INDEX[ac] for ac in asset_classes]
        corr = scenario.correlation_override[indices][:, indices].copy()
    else:
        corr = get_sub_correlation_matrix(asset_classes)

    # Compute stressed VaR
    stressed_result = calculate_portfolio_var(
        stressed_positions, calculation_type, confidence_level, time_horizon_days,
        volatility_provider=vol_provider, correlation_matrix=corr,
    )

    # Compute per-asset-class impacts
    stressed_exposures: dict[AssetClass, float] = {}
    for pos in stressed_positions:
        stressed_exposures[pos.asset_class] = stressed_exposures.get(pos.asset_class, 0.0) + pos.market_value

    asset_class_impacts = []
    total_pnl = 0.0
    for ac in asset_classes:
        base_exp = base_exposures.get(ac, 0.0)
        stressed_exp = stressed_exposures.get(ac, 0.0)
        pnl = stressed_exp - base_exp
        total_pnl += pnl
        asset_class_impacts.append(AssetClassImpact(
            asset_class=ac,
            base_exposure=base_exp,
            stressed_exposure=stressed_exp,
            pnl_impact=pnl,
        ))

    return StressTestResult(
        scenario_name=scenario.name,
        base_var=base_result.var_value,
        stressed_var=stressed_result.var_value,
        pnl_impact=total_pnl,
        asset_class_impacts=asset_class_impacts,
    )
