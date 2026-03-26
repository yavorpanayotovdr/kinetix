"""SA-CCR: Standardised Approach for Counterparty Credit Risk (BCBS 279).

This module implements the Basel Committee's SA-CCR framework for computing
Exposure at Default (EAD) for OTC derivative portfolios.

SA-CCR is the REGULATORY capital model — it is distinct from and coexists with
the internal Monte Carlo PFE model in credit_exposure.py.

  | Model         | Purpose                     | Method                     |
  |---------------|-----------------------------|----------------------------|
  | SA-CCR        | Regulatory capital (BCBS 279)| Deterministic, formulaic   |
  | MC PFE        | Internal economic limit mgmt | Monte Carlo GBM simulation |

Key formula (BCBS 279 §131):
    EAD = alpha × (RC + PFE)

where:
    alpha = 1.4 (regulatory constant)
    RC    = max(V - C, 0)           replacement cost
    PFE   = multiplier × AddOn      potential future exposure
    AddOn = sum of hedging-set addons
    multiplier = min(1, 0.05 + 0.95 × exp((V - C) / (2 × 0.05 × AddOn)))

References:
    BCBS 279, March 2014 — "The standardised approach for measuring
    counterparty credit risk exposures".
"""
from __future__ import annotations

import math
from dataclasses import dataclass
from itertools import groupby
from typing import Any

from kinetix_risk.black_scholes import bs_delta
from kinetix_risk.models import (
    AssetClass,
    FxPosition,
    OptionPosition,
    PositionRisk,
    SwapPosition,
)

# ---------------------------------------------------------------------------
# Regulatory constants (BCBS 279 Table 2)
# ---------------------------------------------------------------------------

# Supervisory factors by asset class (annualised volatility proxies)
_SUPERVISORY_FACTORS: dict[str, float] = {
    "IR": 0.005,
    "FX": 0.04,
    "CREDIT_IG": 0.0038,
    "CREDIT_HY": 0.05,
    "EQUITY": 0.32,
    "COMMODITY": 0.18,
}

# alpha constant (BCBS 279 §131)
_ALPHA = 1.4

# Multiplier floor (BCBS 279 §149)
_MULTIPLIER_FLOOR = 0.05

# Multiplier parameter (BCBS 279 §149)
_MULTIPLIER_PARAM = 0.95


# ---------------------------------------------------------------------------
# Data models
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class SaCcrTradeClassification:
    """Per-trade inputs to the SA-CCR add-on calculation."""

    trade_id: str
    asset_class: str          # "IR", "FX", "EQUITY", "CREDIT_IG", "CREDIT_HY", "COMMODITY"
    hedging_set: str          # IR→currency, FX→pair, EQUITY/CREDIT→issuer, COMMODITY→type
    supervisory_delta: float  # +1 long, -1 short, or bs_delta for options
    adjusted_notional: float  # per BCBS 279 §157–166
    maturity_factor: float    # sqrt(min(M, 1)) for unmargined trades
    supervisory_factor: float # asset-class supervisory factor from §163


@dataclass(frozen=True)
class HedgingSetAddon:
    """Add-on contribution from one hedging set."""

    asset_class: str
    hedging_set_key: str
    addon_amount: float
    position_count: int


@dataclass(frozen=True)
class SaCcrResult:
    """SA-CCR EAD result for a netting set.

    Labelled explicitly as SA-CCR output — not to be confused with the
    internal Monte Carlo PFE result from credit_exposure.py.
    """

    replacement_cost: float  # RC = max(V - C, 0)
    pfe_addon: float         # SA-CCR regulatory PFE add-on (NOT MC PFE)
    multiplier: float        # exposure multiplier ∈ [0.05, 1]
    ead: float               # EAD = 1.4 × (RC + multiplier × AddOn)
    alpha: float             # regulatory constant = 1.4


# ---------------------------------------------------------------------------
# Classification
# ---------------------------------------------------------------------------


def _asset_class_key(position: PositionRisk | OptionPosition) -> str:
    """Map domain AssetClass to SA-CCR asset class string.

    Credit positions use the credit_subtype field to distinguish
    CREDIT_IG (SF=0.0038) from CREDIT_HY (SF=0.05) per BCBS 279 Table 2.
    """
    # Check credit subtype first — CREDIT_IG/CREDIT_HY have distinct supervisory factors
    if hasattr(position, "credit_subtype") and position.credit_subtype in ("CREDIT_IG", "CREDIT_HY"):
        return position.credit_subtype
    ac = position.asset_class
    if ac == AssetClass.FIXED_INCOME:
        return "IR"
    if ac == AssetClass.FX:
        return "FX"
    if ac == AssetClass.EQUITY:
        return "EQUITY"
    if ac == AssetClass.COMMODITY:
        return "COMMODITY"
    return "EQUITY"


def _hedging_set_key(position: PositionRisk | OptionPosition) -> str:
    """Determine the hedging set for a position (BCBS 279 §175–178)."""
    ac = position.asset_class
    if ac == AssetClass.FIXED_INCOME:
        return position.currency
    if ac == AssetClass.FX:
        if isinstance(position, FxPosition) and position.base_currency and position.quote_currency:
            pair = sorted([position.base_currency, position.quote_currency])
            return f"{pair[0]}/{pair[1]}"
        return position.currency
    # EQUITY options: hedging set is the underlying
    if isinstance(position, OptionPosition):
        return position.underlying_id
    # EQUITY, COMMODITY, CREDIT: grouped by issuer / reference entity / type
    return position.instrument_id


def _supervisory_delta(position: PositionRisk) -> float:
    """Compute supervisory delta for a trade (BCBS 279 §159)."""
    if isinstance(position, OptionPosition):
        return bs_delta(position)
    # Linear: +1 if long (positive MtM / notional), -1 if short
    if isinstance(position, SwapPosition):
        # PAY_FIXED is equivalent to being long the fixed rate (pays fixed, receives float)
        return 1.0 if position.pay_receive == "PAY_FIXED" else -1.0
    return 1.0 if position.market_value >= 0.0 else -1.0


def _adjusted_notional(position: PositionRisk | OptionPosition) -> float:
    """Compute adjusted notional (BCBS 279 §157–166).

    - IR swaps:    contractual notional
    - FX:          |market_value| as proxy for notional amount
    - Equity:      |market_value| (quantity × price)
    - Options:     spot_price × quantity × contract_multiplier (underlying notional)
    """
    if isinstance(position, OptionPosition):
        return abs(position.spot_price * position.quantity * position.contract_multiplier)
    if isinstance(position, SwapPosition) and position.notional > 0:
        return abs(position.notional)
    if isinstance(position, FxPosition):
        return abs(position.market_value)
    # Equity and other PositionRisk subtypes: market value is the adjusted notional
    return abs(position.market_value)


def _maturity_factor(position: PositionRisk | OptionPosition) -> float:
    """Compute maturity factor for unmargined trades (BCBS 279 §164).

    MF = sqrt(min(M, 1))

    For positions without explicit maturity, assume M >= 1 year → MF = 1.0.
    Options use expiry in days for maturity.
    """
    from datetime import date

    maturity_years = 1.0

    if isinstance(position, OptionPosition):
        maturity_years = max(position.expiry_days / 365.0, 0.0)
    elif isinstance(position, SwapPosition) and position.maturity_date:
        try:
            maturity = date.fromisoformat(position.maturity_date)
            today = date.today()
            remaining_days = (maturity - today).days
            maturity_years = max(remaining_days / 365.0, 0.0)
        except ValueError:
            maturity_years = 1.0

    return math.sqrt(min(maturity_years, 1.0))


def classify_trade(
    position: PositionRisk | OptionPosition,
    market_data: dict[str, Any],
) -> SaCcrTradeClassification:
    """Classify a single trade for SA-CCR add-on calculation.

    Parameters
    ----------
    position:
        Domain position (PositionRisk or subclass).
    market_data:
        Market data bundle — not required for the current classification logic
        but passed for extensibility (e.g. future credit-quality lookup).

    Returns
    -------
    SaCcrTradeClassification
        Fully populated classification ready for hedging-set aggregation.
    """
    ac_key = _asset_class_key(position)
    sf = _SUPERVISORY_FACTORS[ac_key]

    return SaCcrTradeClassification(
        trade_id=position.instrument_id,
        asset_class=ac_key,
        hedging_set=_hedging_set_key(position),
        supervisory_delta=_supervisory_delta(position),
        adjusted_notional=_adjusted_notional(position),
        maturity_factor=_maturity_factor(position),
        supervisory_factor=sf,
    )


# ---------------------------------------------------------------------------
# Hedging set aggregation
# ---------------------------------------------------------------------------


def aggregate_hedging_sets(
    classifications: list[SaCcrTradeClassification],
) -> list[HedgingSetAddon]:
    """Aggregate per-trade classifications into hedging set add-ons.

    For each (asset_class, hedging_set_key) group:
        addon = SF × |sum(delta_i × AN_i × MF_i)|

    This applies the simplified BCBS 279 aggregation for a single hedging set
    where offsets between longs and shorts are permitted within the set.
    """
    if not classifications:
        return []

    # Sort for deterministic groupby behaviour
    sorted_cls = sorted(classifications, key=lambda c: (c.asset_class, c.hedging_set))
    addons: list[HedgingSetAddon] = []

    for (ac, hs), group in groupby(sorted_cls, key=lambda c: (c.asset_class, c.hedging_set)):
        group_list = list(group)
        # Effective notional: directional sum (longs add, shorts subtract)
        effective_notional = sum(
            c.supervisory_delta * c.adjusted_notional * c.maturity_factor
            for c in group_list
        )
        # Supervisory factor from the first trade in the group (all same asset class)
        sf = group_list[0].supervisory_factor
        addon_amount = sf * abs(effective_notional)

        addons.append(
            HedgingSetAddon(
                asset_class=ac,
                hedging_set_key=hs,
                addon_amount=addon_amount,
                position_count=len(group_list),
            )
        )

    return addons


# ---------------------------------------------------------------------------
# EAD calculation
# ---------------------------------------------------------------------------


def _compute_multiplier(netting_set_mtm: float, collateral_net: float, addon: float) -> float:
    """Compute the SA-CCR exposure multiplier (BCBS 279 §149).

    multiplier = min(1, floor + (1 - floor) × exp((V - C) / (2 × floor × AddOn)))

    floor = 0.05 — prevents the multiplier from reaching zero, ensuring that
    over-collateralisation only reduces, not eliminates, PFE.

    If AddOn is zero (no positions or perfect offset), returns floor to avoid
    division by zero while respecting the regulatory intent.
    """
    if addon <= 0.0:
        return _MULTIPLIER_FLOOR

    v_minus_c = netting_set_mtm - collateral_net
    exponent = v_minus_c / (2.0 * _MULTIPLIER_FLOOR * addon)
    multiplier = _MULTIPLIER_FLOOR + _MULTIPLIER_PARAM * math.exp(exponent)
    return min(1.0, multiplier)


def calculate_sa_ccr(
    positions: list[PositionRisk | OptionPosition],
    market_data: dict[str, Any],
    collateral_net: float = 0.0,
) -> SaCcrResult:
    """Compute SA-CCR EAD for a netting set (BCBS 279 §131).

    This is the REGULATORY capital calculation.  It is distinct from the
    internal Monte Carlo PFE exposure model — do not conflate the two.

    Parameters
    ----------
    positions:
        All positions belonging to the netting set.
    market_data:
        Market data bundle passed through to classification.
    collateral_net:
        Net collateral held after applying haircuts: C in RC = max(V - C, 0).
        Positive = we hold collateral; negative = we have posted collateral.

    Returns
    -------
    SaCcrResult
        EAD and its decomposition (RC, PFE add-on, multiplier).
    """
    if not positions:
        return SaCcrResult(
            replacement_cost=0.0,
            pfe_addon=0.0,
            multiplier=_MULTIPLIER_FLOOR,
            ead=0.0,
            alpha=_ALPHA,
        )

    # Classify every trade in the netting set
    classifications = [classify_trade(p, market_data) for p in positions]

    # Aggregate into hedging set add-ons
    hedging_set_addons = aggregate_hedging_sets(classifications)
    total_addon = sum(h.addon_amount for h in hedging_set_addons)

    # Netting set current MtM (sum of all position market values)
    from kinetix_risk.black_scholes import bs_price as _bs_price
    netting_set_mtm = sum(
        _bs_price(p) * p.quantity if isinstance(p, OptionPosition) else p.market_value
        for p in positions
    )

    # Replacement cost: max(V - C, 0)
    rc = max(netting_set_mtm - collateral_net, 0.0)

    # Exposure multiplier — accounts for over-collateralisation
    multiplier = _compute_multiplier(netting_set_mtm, collateral_net, total_addon)

    # PFE add-on
    pfe_addon = multiplier * total_addon

    # EAD = alpha × (RC + PFE)
    ead = _ALPHA * (rc + pfe_addon)

    return SaCcrResult(
        replacement_cost=rc,
        pfe_addon=pfe_addon,
        multiplier=multiplier,
        ead=ead,
        alpha=_ALPHA,
    )
