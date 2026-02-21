import math

from kinetix_risk.frtb.risk_weights import (
    INTER_BUCKET_CORRELATIONS,
    INTRA_BUCKET_CORRELATION,
    RISK_WEIGHTS,
    VEGA_RISK_WEIGHTS,
    asset_class_to_risk_classes,
)
from kinetix_risk.models import (
    FrtbRiskClass,
    PositionRisk,
    RiskClassCharge,
    SbmResult,
    SensitivityInput,
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

    # Apply intra-bucket correlation (reduces charges for diversified buckets)
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

    if sensitivities is None:
        sensitivities = _derive_sensitivities(positions)

    # Group sensitivities by risk class
    by_rc: dict[FrtbRiskClass, list[SensitivityInput]] = {}
    for s in sensitivities:
        by_rc.setdefault(s.risk_class, []).append(s)

    charges = []
    for rc in FrtbRiskClass:
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
