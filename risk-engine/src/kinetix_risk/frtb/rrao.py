from kinetix_risk.models import AssetClass, PositionRisk, RraoResult

_EXOTIC_RATE = 0.01
_OTHER_RATE = 0.001


def calculate_rrao(positions: list[PositionRisk]) -> RraoResult:
    if not positions:
        return RraoResult(exotic_notional=0.0, other_notional=0.0, total_rrao=0.0)

    exotic_notional = 0.0
    other_notional = 0.0

    for pos in positions:
        notional = abs(pos.market_value)
        if pos.asset_class == AssetClass.DERIVATIVE:
            exotic_notional += notional
        else:
            other_notional += notional

    exotic_charge = exotic_notional * _EXOTIC_RATE
    other_charge = other_notional * _OTHER_RATE
    total_rrao = exotic_charge + other_charge

    return RraoResult(
        exotic_notional=exotic_notional,
        other_notional=other_notional,
        total_rrao=total_rrao,
    )
