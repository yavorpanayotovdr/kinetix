from collections import defaultdict

from kinetix_risk.models import (
    AssetClass, CalculationType, ConfidenceLevel, GreeksResult, PositionRisk,
)
from kinetix_risk.portfolio_risk import calculate_portfolio_var
from kinetix_risk.volatility import DEFAULT_VOLATILITIES, VolatilityProvider

PRICE_BUMP = 0.01       # 1% price bump for delta/gamma
VOL_BUMP = 0.01         # 1 percentage point vol bump for vega
RATE_BUMP = 0.0001      # 1 basis point for rho
DEFAULT_RISK_FREE_RATE = 0.05


def _bump_positions(positions: list[PositionRisk], asset_class: AssetClass, bump: float) -> list[PositionRisk]:
    result = []
    for pos in positions:
        if pos.asset_class == asset_class:
            result.append(PositionRisk(
                instrument_id=pos.instrument_id,
                asset_class=pos.asset_class,
                market_value=pos.market_value * (1 + bump),
                currency=pos.currency,
            ))
        else:
            result.append(pos)
    return result


def _var_value(positions, calculation_type, confidence_level, time_horizon_days,
               volatility_provider=None, risk_free_rate: float = 0.0) -> float:
    return calculate_portfolio_var(
        positions, calculation_type, confidence_level, time_horizon_days,
        volatility_provider=volatility_provider,
        risk_free_rate=risk_free_rate,
    ).var_value


def calculate_greeks(
    positions: list[PositionRisk],
    calculation_type: CalculationType,
    confidence_level: ConfidenceLevel,
    time_horizon_days: int,
    portfolio_id: str = "",
    base_var_value: float | None = None,
) -> GreeksResult:
    if not positions:
        raise ValueError("Cannot calculate Greeks on empty positions list")

    base_var = base_var_value if base_var_value is not None else _var_value(
        positions, calculation_type, confidence_level, time_horizon_days
    )

    # Find which asset classes are present
    asset_classes_present: set[AssetClass] = set()
    for pos in positions:
        asset_classes_present.add(pos.asset_class)

    delta: dict[AssetClass, float] = {}
    gamma: dict[AssetClass, float] = {}
    vega: dict[AssetClass, float] = {}

    for ac in sorted(asset_classes_present, key=lambda a: a.value):
        # Delta: (VaR_up - VaR_base) / bump
        positions_up = _bump_positions(positions, ac, PRICE_BUMP)
        var_up = _var_value(positions_up, calculation_type, confidence_level, time_horizon_days)
        delta[ac] = (var_up - base_var) / PRICE_BUMP

        # Gamma: (VaR_up - 2*VaR_base + VaR_down) / bump^2
        positions_down = _bump_positions(positions, ac, -PRICE_BUMP)
        var_down = _var_value(positions_down, calculation_type, confidence_level, time_horizon_days)
        gamma[ac] = (var_up - 2 * base_var + var_down) / (PRICE_BUMP ** 2)

        # Vega: bump vol by +1pp, (VaR_bumped - VaR_base) / vol_bump
        base_vol = DEFAULT_VOLATILITIES[ac]
        bumped_vols = dict(DEFAULT_VOLATILITIES)
        bumped_vols[ac] = base_vol + VOL_BUMP
        vol_provider = VolatilityProvider.from_dict(bumped_vols)
        var_vol_up = _var_value(positions, calculation_type, confidence_level, time_horizon_days,
                                volatility_provider=vol_provider)
        vega[ac] = (var_vol_up - base_var) / VOL_BUMP

    # Theta: VaR with (time_horizon - 1) minus VaR_base
    if time_horizon_days > 1:
        var_t_minus_1 = _var_value(positions, calculation_type, confidence_level, time_horizon_days - 1)
    else:
        # For 1-day horizon, compute with 2-day to show time sensitivity
        var_t_plus_1 = _var_value(positions, calculation_type, confidence_level, time_horizon_days + 1)
        var_t_minus_1 = var_t_plus_1  # theta = VaR(t+1) - VaR(t)

    theta = var_t_minus_1 - base_var

    # Rho: bump risk-free rate by 1bp and measure VaR change
    var_base_rate = _var_value(positions, calculation_type, confidence_level, time_horizon_days,
                               risk_free_rate=DEFAULT_RISK_FREE_RATE)
    var_bumped_rate = _var_value(positions, calculation_type, confidence_level, time_horizon_days,
                                 risk_free_rate=DEFAULT_RISK_FREE_RATE + RATE_BUMP)
    rho = (var_bumped_rate - var_base_rate) / RATE_BUMP

    return GreeksResult(
        portfolio_id=portfolio_id,
        delta=delta,
        gamma=gamma,
        vega=vega,
        theta=theta,
        rho=rho,
    )
