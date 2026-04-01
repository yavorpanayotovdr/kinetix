"""Resolve typed positions into effective linear exposures for VaR.

Options need delta-adjusted exposure instead of raw premium (market_value),
because the option premium dramatically understates the actual directional risk.
Swaps need DV01-based exposure because swap PV at inception is near zero,
which dramatically understates interest-rate sensitivity.
Other position types pass through unchanged — their market_value is already
a reasonable linear exposure measure.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

from kinetix_risk.models import OptionPosition, PositionRisk, SwapPosition

if TYPE_CHECKING:
    from kinetix_risk.market_data_consumer import MarketDataBundle

_DEFAULT_IMPLIED_VOL = 0.25


def resolve_positions(
    positions: list[PositionRisk],
    bundle: "MarketDataBundle | None" = None,
) -> list[PositionRisk]:
    """Convert typed positions into effective linear exposures for VaR.

    If a MarketDataBundle is provided, OptionPositions with missing spot or
    vol data are enriched from the bundle before delta-adjustment.
    """
    resolved = []
    for pos in positions:
        if isinstance(pos, OptionPosition):
            pos = _enrich_option(pos, bundle)
            if pos.spot_price > 0 and pos.implied_vol > 0:
                from kinetix_risk.black_scholes import bs_delta
                delta = bs_delta(pos)
                effective_mv = delta * pos.quantity * pos.spot_price * pos.contract_multiplier
                resolved.append(PositionRisk(
                    instrument_id=pos.instrument_id,
                    asset_class=pos.asset_class,
                    market_value=effective_mv,
                    currency=pos.currency,
                ))
            else:
                resolved.append(pos)
        elif isinstance(pos, SwapPosition):
            resolved.append(_resolve_swap(pos, bundle))
        else:
            resolved.append(pos)
    return resolved


def _enrich_option(pos: OptionPosition, bundle: "MarketDataBundle | None") -> OptionPosition:
    """Return a new OptionPosition with spot_price and implied_vol filled from the bundle.

    Only overwrites fields that are zero/missing; does not clobber existing values.
    """
    if bundle is None:
        return pos

    spot = pos.spot_price
    vol = pos.implied_vol

    if spot == 0.0:
        spot = bundle.spot_prices.get(pos.underlying_id, 0.0)

    if vol == 0.0:
        surface = bundle.vol_surfaces.get(pos.underlying_id)
        if surface is not None:
            try:
                vol = surface.vol_at(pos.strike, pos.expiry_days)
            except Exception:
                vol = _DEFAULT_IMPLIED_VOL
        else:
            vol = _DEFAULT_IMPLIED_VOL if spot > 0.0 else 0.0

    if spot == pos.spot_price and vol == pos.implied_vol:
        return pos

    # frozen dataclass — create a new instance with the enriched values
    return OptionPosition(
        instrument_id=pos.instrument_id,
        underlying_id=pos.underlying_id,
        option_type=pos.option_type,
        strike=pos.strike,
        expiry_days=pos.expiry_days,
        spot_price=spot,
        implied_vol=vol,
        risk_free_rate=pos.risk_free_rate,
        quantity=pos.quantity,
        currency=pos.currency,
        dividend_yield=pos.dividend_yield,
        contract_multiplier=pos.contract_multiplier,
        asset_class=pos.asset_class,
    )


def _resolve_swap(pos: SwapPosition, bundle: "MarketDataBundle | None") -> PositionRisk:
    """Convert a swap to DV01-based effective exposure for VaR.

    If no yield curve is available for the swap's currency, the swap passes
    through unchanged (graceful degradation to market_value).
    """
    if bundle is None:
        return pos

    yield_curve = bundle.yield_curves.get(pos.currency)
    if yield_curve is None:
        return pos

    from kinetix_risk.swap_pricing import swap_dv01
    dv01 = swap_dv01(pos, yield_curve)
    return PositionRisk(
        instrument_id=pos.instrument_id,
        asset_class=pos.asset_class,
        market_value=dv01,
        currency=pos.currency,
    )
