from dataclasses import dataclass
from enum import Enum

import numpy as np


class AssetClass(Enum):
    EQUITY = "EQUITY"
    FIXED_INCOME = "FIXED_INCOME"
    FX = "FX"
    COMMODITY = "COMMODITY"
    DERIVATIVE = "DERIVATIVE"


class CalculationType(Enum):
    HISTORICAL = "HISTORICAL"
    PARAMETRIC = "PARAMETRIC"
    MONTE_CARLO = "MONTE_CARLO"


class ConfidenceLevel(Enum):
    CL_95 = 0.95
    CL_99 = 0.99


@dataclass(frozen=True)
class PositionRisk:
    instrument_id: str
    asset_class: AssetClass
    market_value: float
    currency: str


@dataclass(frozen=True)
class AssetClassExposure:
    asset_class: AssetClass
    total_market_value: float
    volatility: float


@dataclass(frozen=True)
class ComponentBreakdown:
    asset_class: AssetClass
    var_contribution: float
    percentage_of_total: float


@dataclass(frozen=True)
class VaRResult:
    var_value: float
    expected_shortfall: float
    component_breakdown: list[ComponentBreakdown]


@dataclass(frozen=True)
class AssetClassImpact:
    asset_class: AssetClass
    base_exposure: float
    stressed_exposure: float
    pnl_impact: float


@dataclass(frozen=True)
class StressScenario:
    name: str
    description: str
    vol_shocks: dict[AssetClass, float]
    correlation_override: np.ndarray | None
    price_shocks: dict[AssetClass, float]


@dataclass(frozen=True)
class StressTestResult:
    scenario_name: str
    base_var: float
    stressed_var: float
    pnl_impact: float
    asset_class_impacts: list[AssetClassImpact]


@dataclass(frozen=True)
class GreeksResult:
    portfolio_id: str
    delta: dict[AssetClass, float]
    gamma: dict[AssetClass, float]
    vega: dict[AssetClass, float]
    theta: float
    rho: float
