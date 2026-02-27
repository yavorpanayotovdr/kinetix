from dataclasses import dataclass, field

from kinetix_risk.models import AssetClass, PositionRisk


@dataclass(frozen=True)
class MarketDataDependency:
    data_type: str
    instrument_id: str
    asset_class: str
    required: bool
    description: str
    parameters: dict[str, str] = field(default_factory=dict)


@dataclass(frozen=True)
class _DependencyTemplate:
    data_type: str
    per_instrument: bool
    required: bool
    description: str
    parameters: dict[str, str] = field(default_factory=dict)
    currency_parameter: str | None = None


DEPENDENCIES_REGISTRY: dict[AssetClass, list[_DependencyTemplate]] = {
    AssetClass.EQUITY: [
        _DependencyTemplate(
            data_type="SPOT_PRICE",
            per_instrument=True,
            required=True,
            description="Current spot price for equity position valuation",
        ),
        _DependencyTemplate(
            data_type="HISTORICAL_PRICES",
            per_instrument=True,
            required=False,
            description="Historical price series for volatility estimation",
            parameters={"lookbackDays": "252"},
        ),
    ],
    AssetClass.FIXED_INCOME: [
        _DependencyTemplate(
            data_type="YIELD_CURVE",
            per_instrument=False,
            required=True,
            description="Risk-free yield curve for discounting cash flows",
            currency_parameter="curveId",
        ),
        _DependencyTemplate(
            data_type="CREDIT_SPREAD",
            per_instrument=True,
            required=True,
            description="Credit spread for issuer-specific risk pricing",
        ),
    ],
    AssetClass.FX: [
        _DependencyTemplate(
            data_type="SPOT_PRICE",
            per_instrument=True,
            required=True,
            description="Current FX spot rate",
        ),
        _DependencyTemplate(
            data_type="FORWARD_CURVE",
            per_instrument=True,
            required=False,
            description="FX forward curve for forward rate estimation",
        ),
    ],
    AssetClass.COMMODITY: [
        _DependencyTemplate(
            data_type="SPOT_PRICE",
            per_instrument=True,
            required=True,
            description="Current commodity spot price",
        ),
        _DependencyTemplate(
            data_type="FORWARD_CURVE",
            per_instrument=True,
            required=False,
            description="Commodity forward curve for futures pricing",
        ),
    ],
    AssetClass.DERIVATIVE: [
        _DependencyTemplate(
            data_type="SPOT_PRICE",
            per_instrument=True,
            required=True,
            description="Underlying spot price for derivative valuation",
        ),
        _DependencyTemplate(
            data_type="VOLATILITY_SURFACE",
            per_instrument=True,
            required=True,
            description="Implied volatility surface for option pricing",
        ),
        _DependencyTemplate(
            data_type="RISK_FREE_RATE",
            per_instrument=False,
            required=True,
            description="Risk-free interest rate for discounting",
            currency_parameter="currency",
        ),
        _DependencyTemplate(
            data_type="DIVIDEND_YIELD",
            per_instrument=True,
            required=False,
            description="Expected dividend yield for underlying asset",
        ),
    ],
}


def discover(positions: list[PositionRisk]) -> list[MarketDataDependency]:
    seen: set[tuple[str, str, frozenset]] = set()
    result: list[MarketDataDependency] = []

    for pos in positions:
        templates = DEPENDENCIES_REGISTRY.get(pos.asset_class, [])
        for tmpl in templates:
            instrument_id = pos.instrument_id if tmpl.per_instrument else ""
            params = dict(tmpl.parameters)
            if tmpl.currency_parameter is not None:
                params[tmpl.currency_parameter] = pos.currency
            key = (tmpl.data_type, instrument_id, frozenset(params.items()))
            if key in seen:
                continue
            seen.add(key)
            result.append(MarketDataDependency(
                data_type=tmpl.data_type,
                instrument_id=instrument_id,
                asset_class=pos.asset_class.value,
                required=tmpl.required,
                description=tmpl.description,
                parameters=params,
            ))

    # Add portfolio-level correlation matrix if there are 2+ distinct asset classes
    asset_classes = {pos.asset_class for pos in positions}
    if len(asset_classes) >= 2:
        key = ("CORRELATION_MATRIX", "", frozenset())
        if key not in seen:
            seen.add(key)
            result.append(MarketDataDependency(
                data_type="CORRELATION_MATRIX",
                instrument_id="",
                asset_class="",
                required=True,
                description="Cross-asset correlation matrix for portfolio diversification",
            ))

    return result
