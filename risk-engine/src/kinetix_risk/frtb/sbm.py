import math
from datetime import date, datetime

import numpy as np

from kinetix_risk.frtb.girr_correlations import (
    GIRR_TENOR_YEARS,
    STANDARD_CORRELATION_MATRIX,
)
from kinetix_risk.frtb.risk_weights import (
    GIRR_RISK_WEIGHTS,
    INTER_BUCKET_CORRELATIONS,
    INTRA_BUCKET_CORRELATION,
    RISK_WEIGHTS,
    VEGA_RISK_WEIGHTS,
    asset_class_to_risk_classes,
)
from kinetix_risk.models import (
    AssetClass,
    BondPosition,
    FrtbRiskClass,
    GirrRiskClassCharge,
    PositionRisk,
    RiskClassCharge,
    SbmResult,
    SensitivityInput,
    SwapPosition,
    TenorCharge,
)

# Ordered tenor labels matching GIRR_TENOR_YEARS
_TENOR_LABELS: list[str] = [
    "0.25Y", "0.5Y", "1Y", "2Y", "3Y", "5Y", "10Y", "15Y", "20Y", "30Y",
]


def _years_to_maturity(maturity_date_str: str) -> float:
    """Parse an ISO date string and return fractional years from today."""
    if not maturity_date_str:
        return 5.0
    try:
        maturity = datetime.strptime(maturity_date_str, "%Y-%m-%d").date()
    except ValueError:
        return 5.0
    today = date.today()
    days = (maturity - today).days
    return max(days / 365.25, 0.0)


def _snap_to_tenor_grid(years: float) -> int:
    """Return the index into GIRR_TENOR_YEARS of the nearest standard tenor."""
    return min(
        range(len(GIRR_TENOR_YEARS)),
        key=lambda i: abs(GIRR_TENOR_YEARS[i] - years),
    )


def _estimate_dv01(position: PositionRisk) -> float:
    """
    Estimate DV01 (dollar value of a basis point) from position data.

    BondPosition: DV01 ≈ face_value * modified_duration / 10_000.
    SwapPosition: DV01 ≈ notional * years / 10_000.
    Plain PositionRisk: market_value * 0.01% as a conservative proxy.
    """
    if isinstance(position, BondPosition):
        years = _years_to_maturity(position.maturity_date)
        coupon = position.coupon_rate if position.coupon_rate > 0 else 0.05
        mac_duration = min(years, years * 0.9 + 0.5)
        mod_duration = mac_duration / (1.0 + coupon / 2.0)
        face = position.face_value if position.face_value > 0 else abs(position.market_value)
        return face * mod_duration / 10_000.0

    if isinstance(position, SwapPosition):
        years = _years_to_maturity(position.maturity_date)
        notional = position.notional if position.notional > 0 else abs(position.market_value)
        return notional * years / 10_000.0

    return abs(position.market_value) * 0.0001


def _years_for_position(pos: PositionRisk) -> float:
    """Extract or estimate years to maturity for tenor bucket assignment."""
    if isinstance(pos, (BondPosition, SwapPosition)):
        return _years_to_maturity(pos.maturity_date)
    return 5.0


def _compute_girr_charge(girr_positions: list[PositionRisk]) -> GirrRiskClassCharge:
    """
    Compute the GIRR delta charge using BCBS 352 tenor-specific risk weights
    and intra-bucket correlation aggregation (Table 4).

    Weighted sensitivity per bucket: ws_k = DV01_k * RW_k
    Aggregated charge: sqrt(sum_k sum_l rho(k,l) * ws_k * ws_l)
    """
    dv01_by_bucket: list[float] = [0.0] * len(GIRR_TENOR_YEARS)

    for pos in girr_positions:
        years = _years_for_position(pos)
        bucket_idx = _snap_to_tenor_grid(years)
        dv01_by_bucket[bucket_idx] += _estimate_dv01(pos)

    rw_list = [GIRR_RISK_WEIGHTS[label] for label in _TENOR_LABELS]
    ws = [dv01_by_bucket[i] * rw_list[i] for i in range(len(GIRR_TENOR_YEARS))]

    ws_arr = np.array(ws)
    charge_sq = float(ws_arr @ STANDARD_CORRELATION_MATRIX @ ws_arr)
    delta_charge = math.sqrt(max(charge_sq, 0.0))

    tenor_charges = [
        TenorCharge(
            tenor_label=_TENOR_LABELS[i],
            sensitivity=dv01_by_bucket[i],
            risk_weight=rw_list[i],
            weighted_sensitivity=ws[i],
        )
        for i in range(len(GIRR_TENOR_YEARS))
        if dv01_by_bucket[i] != 0.0
    ]

    # Vega and curvature use the existing flat-rate approximation
    total_market_value = sum(abs(p.market_value) for p in girr_positions)
    vrw = VEGA_RISK_WEIGHTS[FrtbRiskClass.GIRR]
    rw_flat = RISK_WEIGHTS[FrtbRiskClass.GIRR]
    vega_charge = vrw * total_market_value
    curvature_charge = 0.5 * rw_flat * rw_flat * total_market_value

    total = delta_charge + vega_charge + curvature_charge
    return GirrRiskClassCharge(
        risk_class=FrtbRiskClass.GIRR,
        delta_charge=delta_charge,
        vega_charge=vega_charge,
        curvature_charge=curvature_charge,
        total_charge=total,
        tenor_charges=tenor_charges,
    )


def _derive_sensitivities(positions: list[PositionRisk]) -> list[SensitivityInput]:
    rc_exposures: dict[FrtbRiskClass, float] = {}
    for pos in positions:
        for rc in asset_class_to_risk_classes(pos.asset_class):
            rc_exposures[rc] = rc_exposures.get(rc, 0.0) + abs(pos.market_value)

    sensitivities = []
    for rc, exposure in rc_exposures.items():
        rw = RISK_WEIGHTS[rc]
        sensitivities.append(SensitivityInput(
            risk_class=rc,
            delta=exposure * rw,
            vega=exposure * VEGA_RISK_WEIGHTS[rc],
            curvature=exposure * rw,
        ))
    return sensitivities


def _compute_risk_class_charge(
    risk_class: FrtbRiskClass,
    sensitivities: list[SensitivityInput],
) -> RiskClassCharge:
    rw = RISK_WEIGHTS[risk_class]
    vrw = VEGA_RISK_WEIGHTS[risk_class]
    corr = INTRA_BUCKET_CORRELATION[risk_class]

    delta_sum = sum(abs(s.delta) for s in sensitivities)
    vega_sum = sum(abs(s.vega) for s in sensitivities)
    curvature_sum = sum(abs(s.curvature) for s in sensitivities)

    delta_charge = rw * delta_sum
    vega_charge = vrw * vega_sum
    curvature_charge = 0.5 * rw * rw * curvature_sum

    n = len(sensitivities)
    if n > 1:
        diversification = math.sqrt(n * (1 - corr) + corr * n * n) / n
        delta_charge *= diversification
        vega_charge *= diversification
        curvature_charge *= diversification

    total = delta_charge + vega_charge + curvature_charge
    return RiskClassCharge(
        risk_class=risk_class,
        delta_charge=delta_charge,
        vega_charge=vega_charge,
        curvature_charge=curvature_charge,
        total_charge=total,
    )


def _aggregate_across_risk_classes(charges: list[RiskClassCharge]) -> float:
    if not charges:
        return 0.0

    results = []
    for scenario, corr in INTER_BUCKET_CORRELATIONS.items():
        total_sq = 0.0
        totals = [c.total_charge for c in charges]
        for i, ci in enumerate(totals):
            for j, cj in enumerate(totals):
                if i == j:
                    total_sq += ci * ci
                else:
                    total_sq += corr * ci * cj
        results.append(math.sqrt(max(total_sq, 0.0)))

    return max(results)


def calculate_sbm(
    positions: list[PositionRisk],
    sensitivities: list[SensitivityInput] | None = None,
) -> SbmResult:
    if not positions:
        return SbmResult(risk_class_charges=[], total_sbm_charge=0.0)

    girr_positions = [p for p in positions if p.asset_class == AssetClass.FIXED_INCOME]

    if sensitivities is None:
        sensitivities = _derive_sensitivities(positions)

    by_rc: dict[FrtbRiskClass, list[SensitivityInput]] = {}
    for s in sensitivities:
        by_rc.setdefault(s.risk_class, []).append(s)

    charges = []
    for rc in FrtbRiskClass:
        if rc == FrtbRiskClass.GIRR:
            if girr_positions:
                charges.append(_compute_girr_charge(girr_positions))
            else:
                charges.append(GirrRiskClassCharge(
                    risk_class=FrtbRiskClass.GIRR,
                    delta_charge=0.0,
                    vega_charge=0.0,
                    curvature_charge=0.0,
                    total_charge=0.0,
                    tenor_charges=[],
                ))
        else:
            rc_sensitivities = by_rc.get(rc, [])
            if rc_sensitivities:
                charges.append(_compute_risk_class_charge(rc, rc_sensitivities))
            else:
                charges.append(RiskClassCharge(
                    risk_class=rc,
                    delta_charge=0.0,
                    vega_charge=0.0,
                    curvature_charge=0.0,
                    total_charge=0.0,
                ))

    total = _aggregate_across_risk_classes([c for c in charges if c.total_charge > 0])
    return SbmResult(risk_class_charges=charges, total_sbm_charge=total)
