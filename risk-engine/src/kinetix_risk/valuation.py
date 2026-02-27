from kinetix_risk.greeks import calculate_greeks
from kinetix_risk.models import (
    CalculationType,
    ConfidenceLevel,
    PositionRisk,
    ValuationResult,
)
from kinetix_risk.portfolio_risk import calculate_portfolio_var
from kinetix_risk.volatility import VolatilityProvider

_DEFAULT_OUTPUTS = ["VAR", "EXPECTED_SHORTFALL"]


def calculate_valuation(
    positions: list[PositionRisk],
    calculation_type: CalculationType,
    confidence_level: ConfidenceLevel,
    time_horizon_days: int,
    requested_outputs: list[str] | None = None,
    num_simulations: int = 10_000,
    volatility_provider: VolatilityProvider | None = None,
    correlation_matrix=None,
    portfolio_id: str = "",
) -> ValuationResult:
    outputs = requested_outputs if requested_outputs else _DEFAULT_OUTPUTS

    if not positions:
        return ValuationResult(var_result=None, greeks_result=None, computed_outputs=[], pv_value=None)

    need_var = "VAR" in outputs or "EXPECTED_SHORTFALL" in outputs
    need_greeks = "GREEKS" in outputs
    need_pv = "PV" in outputs

    var_result = None
    greeks_result = None
    pv_value = None
    computed = []

    if need_var:
        var_result = calculate_portfolio_var(
            positions=positions,
            calculation_type=calculation_type,
            confidence_level=confidence_level,
            time_horizon_days=time_horizon_days,
            num_simulations=num_simulations,
            volatility_provider=volatility_provider or VolatilityProvider.with_jitter(),
            correlation_matrix=correlation_matrix,
        )
        if "VAR" in outputs:
            computed.append("VAR")
        if "EXPECTED_SHORTFALL" in outputs:
            computed.append("EXPECTED_SHORTFALL")

    if need_greeks:
        base_var = var_result.var_value if var_result is not None else None
        greeks_result = calculate_greeks(
            positions=positions,
            calculation_type=calculation_type,
            confidence_level=confidence_level,
            time_horizon_days=time_horizon_days,
            portfolio_id=portfolio_id,
            base_var_value=base_var,
        )
        computed.append("GREEKS")

    if need_pv:
        pv_value = sum(pos.market_value for pos in positions)
        computed.append("PV")

    return ValuationResult(
        var_result=var_result,
        greeks_result=greeks_result,
        computed_outputs=computed,
        pv_value=pv_value,
    )
