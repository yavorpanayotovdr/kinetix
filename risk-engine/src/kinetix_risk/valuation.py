from __future__ import annotations

from typing import TYPE_CHECKING

from kinetix_risk.greeks import calculate_greeks
from kinetix_risk.models import (
    CalculationType,
    ConfidenceLevel,
    OptionPosition,
    PositionRisk,
    ValuationResult,
)
from kinetix_risk.portfolio_risk import calculate_portfolio_var
from kinetix_risk.position_resolver import resolve_positions
from kinetix_risk.volatility import VolatilityProvider

if TYPE_CHECKING:
    from kinetix_risk.market_data_consumer import MarketDataBundle

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
    book_id: str = "",
    seed: int | None = None,
    market_data_bundle: "MarketDataBundle | None" = None,
) -> ValuationResult:
    outputs = requested_outputs if requested_outputs else _DEFAULT_OUTPUTS

    if not positions:
        return ValuationResult(var_result=None, greeks_result=None, computed_outputs=[], pv_value=None)

    # Resolve typed positions to linear exposures (e.g., delta-adjusted for options).
    # Pass the market data bundle so that options with missing spot/vol can be enriched.
    resolved = resolve_positions(positions, bundle=market_data_bundle)

    need_var = "VAR" in outputs or "EXPECTED_SHORTFALL" in outputs
    need_greeks = "GREEKS" in outputs
    need_pv = "PV" in outputs

    var_result = None
    greeks_result = None
    pv_value = None
    computed = []

    if need_var:
        var_result = calculate_portfolio_var(
            positions=resolved,
            calculation_type=calculation_type,
            confidence_level=confidence_level,
            time_horizon_days=time_horizon_days,
            num_simulations=num_simulations,
            volatility_provider=volatility_provider or VolatilityProvider.static(),
            correlation_matrix=correlation_matrix,
            seed=seed,
        )
        if "VAR" in outputs:
            computed.append("VAR")
        if "EXPECTED_SHORTFALL" in outputs:
            computed.append("EXPECTED_SHORTFALL")

    if need_greeks:
        base_var = var_result.var_value if var_result is not None else None
        greeks_result = calculate_greeks(
            positions=resolved,
            calculation_type=calculation_type,
            confidence_level=confidence_level,
            time_horizon_days=time_horizon_days,
            book_id=book_id,
            base_var_value=base_var,
        )
        computed.append("GREEKS")

    if need_pv:
        pv_value = sum(pos.market_value for pos in positions)
        computed.append("PV")

    # Compute per-position analytical Black-Scholes Greeks for any OptionPosition
    # that has valid market data.  We compute against the (possibly enriched) originals,
    # not the resolved linear exposures, because we want option Greeks, not equity proxy.
    position_greeks: dict | None = None
    if need_greeks:
        pg: dict[str, dict[str, float]] = {}
        # Walk the original positions; enrich using the bundle if we were given one
        from kinetix_risk.position_resolver import _enrich_option
        for pos in positions:
            if isinstance(pos, OptionPosition):
                enriched = _enrich_option(pos, market_data_bundle)
                if enriched.spot_price > 0 and enriched.implied_vol > 0:
                    from kinetix_risk.black_scholes import bs_greeks
                    pg[enriched.instrument_id] = bs_greeks(enriched)
        if pg:
            position_greeks = pg

    return ValuationResult(
        var_result=var_result,
        greeks_result=greeks_result,
        computed_outputs=computed,
        pv_value=pv_value,
        position_greeks=position_greeks,
    )
