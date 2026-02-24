import time

from google.protobuf.timestamp_pb2 import Timestamp

from kinetix.common import types_pb2
from kinetix.risk import market_data_dependencies_pb2, regulatory_reporting_pb2, risk_calculation_pb2, stress_testing_pb2
from kinetix_risk.models import (
    AssetClass, CalculationType, ConfidenceLevel, FrtbResult, FrtbRiskClass,
    GreeksResult, PositionRisk, StressScenario, StressTestResult, VaRResult,
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


_ASSET_CLASS_NAME_TO_DOMAIN = {ac.value: ac for ac in AssetClass}


def proto_stress_request_to_scenario(request) -> StressScenario:
    vol_shocks = {}
    for ac_name, shock in request.vol_shocks.items():
        ac = _ASSET_CLASS_NAME_TO_DOMAIN.get(ac_name)
        if ac:
            vol_shocks[ac] = shock

    price_shocks = {}
    for ac_name, shock in request.price_shocks.items():
        ac = _ASSET_CLASS_NAME_TO_DOMAIN.get(ac_name)
        if ac:
            price_shocks[ac] = shock

    return StressScenario(
        name=request.scenario_name or "hypothetical",
        description=request.description or "",
        vol_shocks=vol_shocks,
        correlation_override=None,
        price_shocks=price_shocks,
    )


def stress_result_to_proto(result: StressTestResult) -> stress_testing_pb2.StressTestResponse:
    impacts = []
    for impact in result.asset_class_impacts:
        proto_ac = _DOMAIN_ASSET_CLASS_TO_PROTO[impact.asset_class]
        impacts.append(stress_testing_pb2.AssetClassImpact(
            asset_class=proto_ac,
            base_exposure=impact.base_exposure,
            stressed_exposure=impact.stressed_exposure,
            pnl_impact=impact.pnl_impact,
        ))

    now = Timestamp()
    now.FromSeconds(int(time.time()))

    return stress_testing_pb2.StressTestResponse(
        scenario_name=result.scenario_name,
        base_var=result.base_var,
        stressed_var=result.stressed_var,
        pnl_impact=result.pnl_impact,
        asset_class_impacts=impacts,
        calculated_at=now,
    )


def greeks_result_to_proto(result: GreeksResult) -> stress_testing_pb2.GreeksResponse:
    asset_class_greeks = []
    for ac in sorted(result.delta.keys(), key=lambda a: a.value):
        proto_ac = _DOMAIN_ASSET_CLASS_TO_PROTO[ac]
        asset_class_greeks.append(stress_testing_pb2.GreekValues(
            asset_class=proto_ac,
            delta=result.delta[ac],
            gamma=result.gamma[ac],
            vega=result.vega[ac],
        ))

    now = Timestamp()
    now.FromSeconds(int(time.time()))

    return stress_testing_pb2.GreeksResponse(
        portfolio_id=result.portfolio_id,
        asset_class_greeks=asset_class_greeks,
        theta=result.theta,
        rho=result.rho,
        calculated_at=now,
    )


_DOMAIN_FRTB_RC_TO_PROTO = {
    FrtbRiskClass.GIRR: regulatory_reporting_pb2.GIRR,
    FrtbRiskClass.CSR_NON_SEC: regulatory_reporting_pb2.CSR_NON_SEC,
    FrtbRiskClass.CSR_SEC_CTP: regulatory_reporting_pb2.CSR_SEC_CTP,
    FrtbRiskClass.CSR_SEC_NON_CTP: regulatory_reporting_pb2.CSR_SEC_NON_CTP,
    FrtbRiskClass.EQUITY: regulatory_reporting_pb2.FRTB_EQUITY,
    FrtbRiskClass.COMMODITY: regulatory_reporting_pb2.FRTB_COMMODITY,
    FrtbRiskClass.FX: regulatory_reporting_pb2.FRTB_FX,
}


def frtb_result_to_proto(result: FrtbResult) -> regulatory_reporting_pb2.FrtbResponse:
    rc_charges = []
    for rcc in result.sbm.risk_class_charges:
        rc_charges.append(regulatory_reporting_pb2.RiskClassCharge(
            risk_class=_DOMAIN_FRTB_RC_TO_PROTO[rcc.risk_class],
            delta_charge=rcc.delta_charge,
            vega_charge=rcc.vega_charge,
            curvature_charge=rcc.curvature_charge,
            total_charge=rcc.total_charge,
        ))

    now = Timestamp()
    now.FromSeconds(int(time.time()))

    return regulatory_reporting_pb2.FrtbResponse(
        portfolio_id=result.portfolio_id,
        sbm=regulatory_reporting_pb2.SbmResult(
            risk_class_charges=rc_charges,
            total_sbm_charge=result.sbm.total_sbm_charge,
        ),
        drc=regulatory_reporting_pb2.DrcResult(
            gross_jtd=result.drc.gross_jtd,
            hedge_benefit=result.drc.hedge_benefit,
            net_drc=result.drc.net_drc,
        ),
        rrao=regulatory_reporting_pb2.RraoResult(
            exotic_notional=result.rrao.exotic_notional,
            other_notional=result.rrao.other_notional,
            total_rrao=result.rrao.total_rrao,
        ),
        total_capital_charge=result.total_capital_charge,
        calculated_at=now,
    )


def report_to_proto(
    portfolio_id: str,
    fmt: int,
    content: str,
) -> regulatory_reporting_pb2.GenerateReportResponse:
    now = Timestamp()
    now.FromSeconds(int(time.time()))

    return regulatory_reporting_pb2.GenerateReportResponse(
        portfolio_id=portfolio_id,
        format=fmt,
        content=content,
        generated_at=now,
    )


_PROTO_MARKET_DATA_TYPE_TO_NAME = {
    risk_calculation_pb2.SPOT_PRICE: "SPOT_PRICE",
    risk_calculation_pb2.HISTORICAL_PRICES: "HISTORICAL_PRICES",
    risk_calculation_pb2.VOLATILITY_SURFACE: "VOLATILITY_SURFACE",
    risk_calculation_pb2.YIELD_CURVE: "YIELD_CURVE",
    risk_calculation_pb2.RISK_FREE_RATE: "RISK_FREE_RATE",
    risk_calculation_pb2.DIVIDEND_YIELD: "DIVIDEND_YIELD",
    risk_calculation_pb2.CREDIT_SPREAD: "CREDIT_SPREAD",
    risk_calculation_pb2.FORWARD_CURVE: "FORWARD_CURVE",
    risk_calculation_pb2.CORRELATION_MATRIX: "CORRELATION_MATRIX",
}


def proto_market_data_to_domain(market_data_list) -> list[dict]:
    result = []
    for md in market_data_list:
        data_type_name = _PROTO_MARKET_DATA_TYPE_TO_NAME.get(
            md.data_type, "UNKNOWN"
        )
        item = {
            "data_type": data_type_name,
            "instrument_id": md.instrument_id,
            "asset_class": md.asset_class,
        }
        value_field = md.WhichOneof("value")
        if value_field == "scalar":
            item["scalar"] = md.scalar
        elif value_field == "time_series":
            item["time_series"] = [
                {
                    "timestamp_seconds": pt.timestamp.seconds,
                    "value": pt.value,
                }
                for pt in md.time_series.points
            ]
        elif value_field == "matrix":
            item["matrix"] = {
                "rows": md.matrix.rows,
                "cols": md.matrix.cols,
                "values": list(md.matrix.values),
                "labels": list(md.matrix.labels),
            }
        elif value_field == "curve":
            item["curve"] = [
                {"tenor": pt.tenor, "value": pt.value}
                for pt in md.curve.points
            ]
        result.append(item)
    return result


_MARKET_DATA_TYPE_NAME_TO_PROTO = {
    "SPOT_PRICE": risk_calculation_pb2.SPOT_PRICE,
    "HISTORICAL_PRICES": risk_calculation_pb2.HISTORICAL_PRICES,
    "VOLATILITY_SURFACE": risk_calculation_pb2.VOLATILITY_SURFACE,
    "YIELD_CURVE": risk_calculation_pb2.YIELD_CURVE,
    "RISK_FREE_RATE": risk_calculation_pb2.RISK_FREE_RATE,
    "DIVIDEND_YIELD": risk_calculation_pb2.DIVIDEND_YIELD,
    "CREDIT_SPREAD": risk_calculation_pb2.CREDIT_SPREAD,
    "FORWARD_CURVE": risk_calculation_pb2.FORWARD_CURVE,
    "CORRELATION_MATRIX": risk_calculation_pb2.CORRELATION_MATRIX,
}


def dependencies_to_proto(
    dependencies: list,
) -> market_data_dependencies_pb2.DataDependenciesResponse:
    proto_deps = []
    for dep in dependencies:
        proto_data_type = _MARKET_DATA_TYPE_NAME_TO_PROTO.get(
            dep.data_type,
            risk_calculation_pb2.MARKET_DATA_TYPE_UNSPECIFIED,
        )
        proto_deps.append(market_data_dependencies_pb2.MarketDataDependency(
            data_type=proto_data_type,
            instrument_id=dep.instrument_id,
            asset_class=dep.asset_class,
            required=dep.required,
            description=dep.description,
            parameters=dep.parameters,
        ))
    return market_data_dependencies_pb2.DataDependenciesResponse(
        dependencies=proto_deps,
    )
