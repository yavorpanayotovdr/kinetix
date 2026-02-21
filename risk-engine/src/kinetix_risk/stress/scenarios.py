import numpy as np

from kinetix_risk.models import AssetClass, StressScenario

# High-correlation matrix for crisis periods (near-1 correlations)
_CRISIS_CORRELATION = np.array([
    #  EQUITY    FI      FX     COMM    DERIV
    [  1.00,   0.60,   0.70,   0.80,   0.90],  # EQUITY
    [  0.60,   1.00,   0.40,   0.30,   0.50],  # FIXED_INCOME
    [  0.70,   0.40,   1.00,   0.60,   0.65],  # FX
    [  0.80,   0.30,   0.60,   1.00,   0.75],  # COMMODITY
    [  0.90,   0.50,   0.65,   0.75,   1.00],  # DERIVATIVE
])

_COVID_CORRELATION = np.array([
    #  EQUITY    FI      FX     COMM    DERIV
    [  1.00,   0.50,   0.60,   0.70,   0.85],  # EQUITY
    [  0.50,   1.00,   0.30,   0.20,   0.40],  # FIXED_INCOME
    [  0.60,   0.30,   1.00,   0.55,   0.60],  # FX
    [  0.70,   0.20,   0.55,   1.00,   0.70],  # COMMODITY
    [  0.85,   0.40,   0.60,   0.70,   1.00],  # DERIVATIVE
])

_EURO_CRISIS_CORRELATION = np.array([
    #  EQUITY    FI      FX     COMM    DERIV
    [  1.00,   0.30,   0.65,   0.50,   0.80],  # EQUITY
    [  0.30,   1.00,   0.20,   0.10,   0.25],  # FIXED_INCOME
    [  0.65,   0.20,   1.00,   0.45,   0.55],  # FX
    [  0.50,   0.10,   0.45,   1.00,   0.50],  # COMMODITY
    [  0.80,   0.25,   0.55,   0.50,   1.00],  # DERIVATIVE
])

HISTORICAL_SCENARIOS: dict[str, StressScenario] = {
    "GFC_2008": StressScenario(
        name="GFC_2008",
        description="Global Financial Crisis 2008: severe equity and commodity selloff with vol spike",
        vol_shocks={
            AssetClass.EQUITY: 3.0,
            AssetClass.FIXED_INCOME: 1.5,
            AssetClass.FX: 2.0,
            AssetClass.COMMODITY: 2.5,
            AssetClass.DERIVATIVE: 3.0,
        },
        correlation_override=_CRISIS_CORRELATION,
        price_shocks={
            AssetClass.EQUITY: 0.60,
            AssetClass.FIXED_INCOME: 0.95,
            AssetClass.FX: 0.85,
            AssetClass.COMMODITY: 0.70,
            AssetClass.DERIVATIVE: 0.55,
        },
    ),
    "COVID_2020": StressScenario(
        name="COVID_2020",
        description="COVID-19 pandemic 2020: rapid equity decline, commodity crash, vol spike",
        vol_shocks={
            AssetClass.EQUITY: 2.5,
            AssetClass.FIXED_INCOME: 1.3,
            AssetClass.FX: 1.8,
            AssetClass.COMMODITY: 2.0,
            AssetClass.DERIVATIVE: 2.5,
        },
        correlation_override=_COVID_CORRELATION,
        price_shocks={
            AssetClass.EQUITY: 0.65,
            AssetClass.FIXED_INCOME: 0.97,
            AssetClass.FX: 0.90,
            AssetClass.COMMODITY: 0.75,
            AssetClass.DERIVATIVE: 0.60,
        },
    ),
    "TAPER_TANTRUM_2013": StressScenario(
        name="TAPER_TANTRUM_2013",
        description="Taper Tantrum 2013: fixed income selloff, moderate equity decline",
        vol_shocks={
            AssetClass.EQUITY: 1.5,
            AssetClass.FIXED_INCOME: 2.0,
            AssetClass.FX: 1.3,
            AssetClass.COMMODITY: 1.2,
            AssetClass.DERIVATIVE: 1.5,
        },
        correlation_override=None,
        price_shocks={
            AssetClass.EQUITY: 0.95,
            AssetClass.FIXED_INCOME: 0.90,
            AssetClass.FX: 0.95,
            AssetClass.COMMODITY: 0.97,
            AssetClass.DERIVATIVE: 0.92,
        },
    ),
    "EURO_CRISIS_2011": StressScenario(
        name="EURO_CRISIS_2011",
        description="European Sovereign Debt Crisis 2011: equity and FX decline, elevated correlations",
        vol_shocks={
            AssetClass.EQUITY: 2.0,
            AssetClass.FIXED_INCOME: 1.5,
            AssetClass.FX: 1.8,
            AssetClass.COMMODITY: 1.3,
            AssetClass.DERIVATIVE: 2.0,
        },
        correlation_override=_EURO_CRISIS_CORRELATION,
        price_shocks={
            AssetClass.EQUITY: 0.80,
            AssetClass.FIXED_INCOME: 0.92,
            AssetClass.FX: 0.85,
            AssetClass.COMMODITY: 0.90,
            AssetClass.DERIVATIVE: 0.75,
        },
    ),
}


def get_scenario(name: str) -> StressScenario:
    scenario = HISTORICAL_SCENARIOS.get(name)
    if scenario is None:
        raise KeyError(f"Unknown stress scenario: {name}")
    return scenario


def list_scenarios() -> list[str]:
    return list(HISTORICAL_SCENARIOS.keys())
