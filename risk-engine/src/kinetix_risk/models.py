from dataclasses import dataclass
from enum import Enum

import numpy as np


class AssetClass(Enum):
    EQUITY = "EQUITY"
    FIXED_INCOME = "FIXED_INCOME"
    FX = "FX"
    COMMODITY = "COMMODITY"
    DERIVATIVE = "DERIVATIVE"


class OptionType(str, Enum):
    CALL = "CALL"
    PUT = "PUT"


@dataclass(frozen=True)
class OptionPosition:
    instrument_id: str
    underlying_id: str
    option_type: OptionType
    strike: float
    expiry_days: int  # days to expiry
    spot_price: float
    implied_vol: float
    risk_free_rate: float = 0.05
    quantity: float = 1.0
    currency: str = "USD"


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


@dataclass(frozen=True)
class ValuationResult:
    var_result: VaRResult | None
    greeks_result: GreeksResult | None
    computed_outputs: list[str]
    pv_value: float | None = None


class FrtbRiskClass(Enum):
    GIRR = "GIRR"
    CSR_NON_SEC = "CSR_NON_SEC"
    CSR_SEC_CTP = "CSR_SEC_CTP"
    CSR_SEC_NON_CTP = "CSR_SEC_NON_CTP"
    EQUITY = "EQUITY"
    COMMODITY = "COMMODITY"
    FX = "FX"


@dataclass(frozen=True)
class SensitivityInput:
    risk_class: FrtbRiskClass
    delta: float
    vega: float
    curvature: float


@dataclass(frozen=True)
class RiskClassCharge:
    risk_class: FrtbRiskClass
    delta_charge: float
    vega_charge: float
    curvature_charge: float
    total_charge: float


@dataclass(frozen=True)
class SbmResult:
    risk_class_charges: list[RiskClassCharge]
    total_sbm_charge: float


@dataclass(frozen=True)
class DrcResult:
    gross_jtd: float
    hedge_benefit: float
    net_drc: float


@dataclass(frozen=True)
class RraoResult:
    exotic_notional: float
    other_notional: float
    total_rrao: float


@dataclass(frozen=True)
class FrtbResult:
    portfolio_id: str
    sbm: SbmResult
    drc: DrcResult
    rrao: RraoResult
    total_capital_charge: float


class TrafficLightZone(str, Enum):
    GREEN = "GREEN"
    YELLOW = "YELLOW"
    RED = "RED"


@dataclass(frozen=True)
class BacktestResult:
    total_days: int
    violation_count: int
    violation_rate: float
    expected_violation_rate: float
    kupiec_statistic: float
    kupiec_p_value: float
    kupiec_pass: bool
    christoffersen_statistic: float
    christoffersen_p_value: float
    christoffersen_pass: bool
    traffic_light_zone: TrafficLightZone
    violations: list[dict]
