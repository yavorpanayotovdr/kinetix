from kinetix_risk.frtb.drc import calculate_drc
from kinetix_risk.frtb.drc_enhanced import calculate_enhanced_drc
from kinetix_risk.frtb.rrao import calculate_rrao
from kinetix_risk.frtb.sbm import calculate_sbm
from kinetix_risk.models import CreditPositionRisk, DrcResult, FrtbResult, PositionRisk, SensitivityInput


def _has_credit_positions(positions: list[PositionRisk]) -> bool:
    return any(isinstance(p, CreditPositionRisk) for p in positions)


def calculate_frtb(
    positions: list[PositionRisk],
    portfolio_id: str,
    sensitivities: list[SensitivityInput] | None = None,
    default_probabilities: dict[str, float] | None = None,
) -> FrtbResult:
    sbm = calculate_sbm(positions, sensitivities)

    if _has_credit_positions(positions):
        enhanced = calculate_enhanced_drc(positions, default_probabilities)
        drc = DrcResult(
            gross_jtd=enhanced.gross_jtd,
            hedge_benefit=enhanced.hedge_benefit,
            net_drc=enhanced.net_drc,
        )
    else:
        drc = calculate_drc(positions, default_probabilities)

    rrao = calculate_rrao(positions)

    total = sbm.total_sbm_charge + drc.net_drc + rrao.total_rrao

    return FrtbResult(
        portfolio_id=portfolio_id,
        sbm=sbm,
        drc=drc,
        rrao=rrao,
        total_capital_charge=total,
    )
