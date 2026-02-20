from dataclasses import dataclass
from enum import Enum


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
