import time

from google.protobuf.timestamp_pb2 import Timestamp

from kinetix.common import types_pb2
from kinetix.risk import risk_calculation_pb2
from kinetix_risk.models import (
    AssetClass, CalculationType, ConfidenceLevel, PositionRisk,
    VaRResult,
)

_PROTO_ASSET_CLASS_TO_DOMAIN = {
    types_pb2.EQUITY: AssetClass.EQUITY,
    types_pb2.FIXED_INCOME: AssetClass.FIXED_INCOME,
    types_pb2.FX: AssetClass.FX,
    types_pb2.COMMODITY: AssetClass.COMMODITY,
    types_pb2.DERIVATIVE: AssetClass.DERIVATIVE,
}

_DOMAIN_ASSET_CLASS_TO_PROTO = {v: k for k, v in _PROTO_ASSET_CLASS_TO_DOMAIN.items()}

_PROTO_CONFIDENCE_TO_DOMAIN = {
    risk_calculation_pb2.CL_95: ConfidenceLevel.CL_95,
    risk_calculation_pb2.CL_99: ConfidenceLevel.CL_99,
}

_PROTO_CALC_TYPE_TO_DOMAIN = {
    risk_calculation_pb2.HISTORICAL: CalculationType.HISTORICAL,
    risk_calculation_pb2.PARAMETRIC: CalculationType.PARAMETRIC,
    risk_calculation_pb2.MONTE_CARLO: CalculationType.MONTE_CARLO,
}


def proto_positions_to_domain(proto_positions) -> list[PositionRisk]:
    result = []
    for p in proto_positions:
        asset_class = _PROTO_ASSET_CLASS_TO_DOMAIN.get(p.asset_class)
        if asset_class is None:
            continue
        market_value = float(p.market_value.amount) if p.market_value.amount else 0.0
        currency = p.market_value.currency or "USD"
        result.append(PositionRisk(
            instrument_id=p.instrument_id.value,
            asset_class=asset_class,
            market_value=market_value,
            currency=currency,
        ))
    return result


def proto_confidence_to_domain(proto_cl) -> ConfidenceLevel:
    cl = _PROTO_CONFIDENCE_TO_DOMAIN.get(proto_cl)
    if cl is None:
        raise ValueError(f"Unknown confidence level: {proto_cl}")
    return cl


def proto_calculation_type_to_domain(proto_ct) -> CalculationType:
    ct = _PROTO_CALC_TYPE_TO_DOMAIN.get(proto_ct)
    if ct is None:
        raise ValueError(f"Unknown calculation type: {proto_ct}")
    return ct


def var_result_to_proto_response(
    result: VaRResult,
    portfolio_id: str,
    calculation_type,
    confidence_level,
) -> risk_calculation_pb2.VaRResponse:
    breakdown = []
    for cb in result.component_breakdown:
        proto_ac = _DOMAIN_ASSET_CLASS_TO_PROTO[cb.asset_class]
        breakdown.append(risk_calculation_pb2.VaRComponentBreakdown(
            asset_class=proto_ac,
            var_contribution=cb.var_contribution,
            percentage_of_total=cb.percentage_of_total,
        ))

    now = Timestamp()
    now.FromSeconds(int(time.time()))

    return risk_calculation_pb2.VaRResponse(
        portfolio_id=types_pb2.PortfolioId(value=portfolio_id),
        calculation_type=calculation_type,
        confidence_level=confidence_level,
        var_value=result.var_value,
        expected_shortfall=result.expected_shortfall,
        component_breakdown=breakdown,
        calculated_at=now,
    )
