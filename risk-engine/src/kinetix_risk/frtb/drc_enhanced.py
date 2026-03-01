from collections import defaultdict

from kinetix_risk.frtb.credit_quality import (
    CREDIT_QUALITY_DEFAULT_PROBS,
    SENIORITY_LGD,
    CreditRating,
    Seniority,
    maturity_weight,
)
from kinetix_risk.frtb.drc import _DEFAULT_LGD, _RATING_DEFAULT_PROBS
from kinetix_risk.models import (
    AssetClass,
    CreditPositionRisk,
    EnhancedDrcResult,
    PositionRisk,
)

_CREDIT_SENSITIVE_CLASSES = {AssetClass.FIXED_INCOME, AssetClass.DERIVATIVE}

_SECTOR_CONCENTRATION_THRESHOLD = 0.50
_SECTOR_CONCENTRATION_SURCHARGE = 0.05


def _compute_jtd_for_credit_position(pos: CreditPositionRisk) -> float:
    try:
        rating = CreditRating(pos.credit_rating)
    except ValueError:
        rating = CreditRating.UNRATED
    dp = CREDIT_QUALITY_DEFAULT_PROBS[rating]

    try:
        seniority = Seniority(pos.seniority)
    except ValueError:
        seniority = Seniority.SENIOR_UNSECURED
    lgd = SENIORITY_LGD[seniority]

    mw = maturity_weight(pos.maturity_years)

    return abs(pos.market_value) * dp * lgd * mw


def _compute_jtd_for_plain_position(
    pos: PositionRisk,
    default_probabilities: dict[str, float] | None,
) -> float:
    if default_probabilities and pos.instrument_id in default_probabilities:
        dp = default_probabilities[pos.instrument_id]
    else:
        dp = _RATING_DEFAULT_PROBS["unrated"]
    return abs(pos.market_value) * dp * _DEFAULT_LGD


def _compute_sector_concentration_charge(
    sector_jtd: dict[str, float],
    gross_jtd: float,
) -> float:
    if gross_jtd == 0:
        return 0.0

    charge = 0.0
    for jtd in sector_jtd.values():
        share = jtd / gross_jtd
        if share > _SECTOR_CONCENTRATION_THRESHOLD:
            excess = jtd - (_SECTOR_CONCENTRATION_THRESHOLD * gross_jtd)
            charge += excess * _SECTOR_CONCENTRATION_SURCHARGE
    return charge


def calculate_enhanced_drc(
    positions: list[PositionRisk],
    default_probabilities: dict[str, float] | None = None,
) -> EnhancedDrcResult:
    if not positions:
        return EnhancedDrcResult(
            gross_jtd=0.0,
            hedge_benefit=0.0,
            sector_concentration_charge=0.0,
            net_drc=0.0,
            jtd_by_rating={},
            jtd_by_seniority={},
        )

    credit_positions = [p for p in positions if p.asset_class in _CREDIT_SENSITIVE_CLASSES]
    if not credit_positions:
        return EnhancedDrcResult(
            gross_jtd=0.0,
            hedge_benefit=0.0,
            sector_concentration_charge=0.0,
            net_drc=0.0,
            jtd_by_rating={},
            jtd_by_seniority={},
        )

    long_jtd = 0.0
    short_jtd = 0.0
    jtd_by_rating: dict[str, float] = defaultdict(float)
    jtd_by_seniority: dict[str, float] = defaultdict(float)
    sector_jtd: dict[str, float] = defaultdict(float)

    for pos in credit_positions:
        if isinstance(pos, CreditPositionRisk):
            jtd = _compute_jtd_for_credit_position(pos)
            rating_key = pos.credit_rating
            seniority_key = pos.seniority
            sector_key = pos.sector
        else:
            jtd = _compute_jtd_for_plain_position(pos, default_probabilities)
            rating_key = "UNRATED"
            seniority_key = "SENIOR_UNSECURED"
            sector_key = "OTHER"

        jtd_by_rating[rating_key] += jtd
        jtd_by_seniority[seniority_key] += jtd
        sector_jtd[sector_key] += jtd

        if pos.market_value >= 0:
            long_jtd += jtd
        else:
            short_jtd += jtd

    gross_jtd = long_jtd + short_jtd
    hedge_benefit = 0.5 * min(long_jtd, short_jtd)
    sector_concentration_charge = _compute_sector_concentration_charge(sector_jtd, gross_jtd)
    net_drc = gross_jtd - hedge_benefit + sector_concentration_charge

    return EnhancedDrcResult(
        gross_jtd=gross_jtd,
        hedge_benefit=hedge_benefit,
        sector_concentration_charge=sector_concentration_charge,
        net_drc=net_drc,
        jtd_by_rating=dict(jtd_by_rating),
        jtd_by_seniority=dict(jtd_by_seniority),
    )
