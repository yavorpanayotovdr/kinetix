from kinetix_risk.models import AssetClass, DrcResult, PositionRisk

_CREDIT_SENSITIVE_CLASSES = {AssetClass.FIXED_INCOME, AssetClass.DERIVATIVE}

_RATING_DEFAULT_PROBS = {
    "investment_grade": 0.003,
    "speculative": 0.03,
    "unrated": 0.015,
}

_DEFAULT_LGD = 0.6


def calculate_drc(
    positions: list[PositionRisk],
    default_probabilities: dict[str, float] | None = None,
) -> DrcResult:
    if not positions:
        return DrcResult(gross_jtd=0.0, hedge_benefit=0.0, net_drc=0.0)

    credit_positions = [p for p in positions if p.asset_class in _CREDIT_SENSITIVE_CLASSES]
    if not credit_positions:
        return DrcResult(gross_jtd=0.0, hedge_benefit=0.0, net_drc=0.0)

    long_jtd = 0.0
    short_jtd = 0.0

    for pos in credit_positions:
        if default_probabilities and pos.instrument_id in default_probabilities:
            dp = default_probabilities[pos.instrument_id]
        else:
            dp = _RATING_DEFAULT_PROBS["unrated"]

        jtd = abs(pos.market_value) * dp * _DEFAULT_LGD

        if pos.market_value >= 0:
            long_jtd += jtd
        else:
            short_jtd += jtd

    gross_jtd = long_jtd + short_jtd
    hedge_benefit = 0.5 * min(long_jtd, short_jtd)
    net_drc = gross_jtd - hedge_benefit

    return DrcResult(
        gross_jtd=gross_jtd,
        hedge_benefit=hedge_benefit,
        net_drc=net_drc,
    )
