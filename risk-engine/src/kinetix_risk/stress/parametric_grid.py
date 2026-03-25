"""
Parametric grid shock generator.

Produces a Cartesian product of shock levels for two risk axes, yielding one
StressScenario per combination.  This is the standard approach for sensitivity
heat-maps used in desk-level stress testing and what-if analysis.
"""
from kinetix_risk.models import AssetClass, ScenarioCategory, StressScenario

# Default grids as specified in the scenario library design
_DEFAULT_EQUITY_RANGE = [-0.30, -0.20, -0.10, 0.0, 0.10]
_DEFAULT_VOL_RANGE = [1.0, 2.0, 3.0, 4.0]

_DEFAULT_RATES_RANGE = [-0.03, -0.02, -0.01, 0.0, 0.01]  # in decimal (300bp = 0.03)
_DEFAULT_CREDIT_RANGE = [0.0, 0.005, 0.01, 0.02]         # credit spread additive shock


def generate_parametric_grid(
    primary_axis: str,
    primary_range: list[float],
    secondary_axis: str,
    secondary_range: list[float],
) -> list[StressScenario]:
    """
    Generate a Cartesian grid of stress scenarios from two shock axes.

    Supported axis values:
      primary_axis:   "equity", "rates"
      secondary_axis: "vol", "credit"

    For "equity" primary axis, primary_range values are fractional price moves
    (e.g. -0.20 means -20%).  The price_shock stored in the scenario is the
    surviving fraction: 1.0 + shock (so -0.20 → 0.80).

    For "rates" primary axis, primary_range values are additive yield moves in
    decimal (e.g. 0.01 = 100bp).  The fixed income price shock is approximated
    as 1.0 - (modified_duration * rate_change) with an assumed duration of 8y,
    representing a broad investment-grade bond index.

    For "vol" secondary axis, secondary_range values are vol multipliers (1.0 = unchanged).

    For "credit" secondary axis, secondary_range values are additive credit spread
    moves in decimal (e.g. 0.01 = 100bp).  The fixed income price shock is
    reduced by (spread_change * 5.0) where 5.0 is an assumed spread duration.
    """
    scenarios = []
    for primary_val in primary_range:
        for secondary_val in secondary_range:
            scenario = _build_scenario(primary_axis, primary_val, secondary_axis, secondary_val)
            scenarios.append(scenario)
    return scenarios


def default_equity_vol_grid() -> list[StressScenario]:
    """Standard equity vs volatility grid: 5 equity levels x 4 vol multipliers."""
    return generate_parametric_grid(
        primary_axis="equity",
        primary_range=_DEFAULT_EQUITY_RANGE,
        secondary_axis="vol",
        secondary_range=_DEFAULT_VOL_RANGE,
    )


def default_rates_credit_grid() -> list[StressScenario]:
    """Standard rates vs credit spread grid: 5 rate moves x 4 credit spread levels."""
    return generate_parametric_grid(
        primary_axis="rates",
        primary_range=_DEFAULT_RATES_RANGE,
        secondary_axis="credit",
        secondary_range=_DEFAULT_CREDIT_RANGE,
    )


def _build_scenario(
    primary_axis: str,
    primary_val: float,
    secondary_axis: str,
    secondary_val: float,
) -> StressScenario:
    primary_label = _format_label(primary_axis, primary_val)
    secondary_label = _format_label(secondary_axis, secondary_val)
    name = f"GRID_{primary_axis.upper()}_{primary_label}_{secondary_axis.upper()}_{secondary_label}"
    description = (
        f"Parametric grid scenario: {primary_axis} {primary_label}, "
        f"{secondary_axis} {secondary_label}"
    )

    price_shocks: dict[AssetClass, float] = {}
    vol_shocks: dict[AssetClass, float] = {}

    if primary_axis == "equity":
        # primary_val is a fractional equity return (e.g. -0.20 means -20%)
        equity_price_shock = max(0.001, 1.0 + primary_val)
        price_shocks[AssetClass.EQUITY] = equity_price_shock
        price_shocks[AssetClass.DERIVATIVE] = max(0.001, 1.0 + primary_val * 0.9)
        price_shocks[AssetClass.FIXED_INCOME] = 1.0
        price_shocks[AssetClass.FX] = 1.0
        price_shocks[AssetClass.COMMODITY] = 1.0

        if secondary_axis == "vol":
            vol_multiplier = max(1.0, secondary_val)
            vol_shocks[AssetClass.EQUITY] = vol_multiplier
            vol_shocks[AssetClass.DERIVATIVE] = vol_multiplier
            vol_shocks[AssetClass.FIXED_INCOME] = max(1.0, 1.0 + (vol_multiplier - 1.0) * 0.3)
            vol_shocks[AssetClass.FX] = max(1.0, 1.0 + (vol_multiplier - 1.0) * 0.5)
            vol_shocks[AssetClass.COMMODITY] = max(1.0, 1.0 + (vol_multiplier - 1.0) * 0.4)

    elif primary_axis == "rates":
        # primary_val is an additive rate move in decimal (0.01 = 100bp)
        # Approximate price impact: dP/P ≈ -duration * dy, assuming duration of 8y
        assumed_duration = 8.0
        fi_price_shock = max(0.001, 1.0 - assumed_duration * primary_val)
        price_shocks[AssetClass.FIXED_INCOME] = fi_price_shock
        # Rising rates modestly negative for equity (via discount rate); small equity impact
        equity_price_shock = max(0.001, 1.0 - primary_val * 2.0)
        price_shocks[AssetClass.EQUITY] = equity_price_shock
        price_shocks[AssetClass.DERIVATIVE] = max(0.001, 1.0 - primary_val * 2.5)
        price_shocks[AssetClass.FX] = 1.0
        price_shocks[AssetClass.COMMODITY] = 1.0

        if secondary_axis == "credit":
            # secondary_val is an additive credit spread move; assumed spread duration 5y
            assumed_spread_duration = 5.0
            credit_price_adjustment = -assumed_spread_duration * secondary_val
            # Apply on top of existing fi shock
            price_shocks[AssetClass.FIXED_INCOME] = max(
                0.001, fi_price_shock + credit_price_adjustment,
            )
            vol_shocks[AssetClass.FIXED_INCOME] = max(
                1.0, 1.0 + secondary_val * 50.0,  # vol scales with spread widening
            )
            vol_shocks[AssetClass.EQUITY] = max(1.0, 1.0 + secondary_val * 20.0)
            vol_shocks[AssetClass.DERIVATIVE] = max(1.0, 1.0 + secondary_val * 25.0)
            vol_shocks[AssetClass.FX] = max(1.0, 1.0 + secondary_val * 10.0)
            vol_shocks[AssetClass.COMMODITY] = 1.0

    # Fill any missing vol shocks with 1.0 (no change)
    for ac in AssetClass:
        if ac not in vol_shocks:
            vol_shocks[ac] = 1.0
        if ac not in price_shocks:
            price_shocks[ac] = 1.0

    return StressScenario(
        name=name,
        description=description,
        vol_shocks=vol_shocks,
        correlation_override=None,
        price_shocks=price_shocks,
        category=ScenarioCategory.INTERNAL_APPROVED,
    )


def _format_label(axis: str, value: float) -> str:
    """Format a shock value into a compact string suitable for a scenario name."""
    if axis == "vol":
        return f"{value:.1f}x"
    elif axis == "credit":
        bps = int(round(value * 10_000))
        return f"{bps:+d}bp"
    else:
        pct = int(round(value * 100))
        return f"{pct:+d}pct"
