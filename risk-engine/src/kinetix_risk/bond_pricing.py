"""DCF bond pricing with DV01 and modified duration."""
from datetime import date

from kinetix_risk.models import BondPosition


def bond_pv(bond: BondPosition, yield_rate: float) -> float:
    """Discount all cash flows at a flat yield."""
    years_to_maturity = _years_to_maturity(bond)
    if years_to_maturity <= 0:
        return bond.face_value  # expired bond returns face

    freq = bond.coupon_frequency or 2
    coupon = bond.face_value * bond.coupon_rate / freq
    periods = int(years_to_maturity * freq)
    if periods <= 0:
        periods = 1

    r = yield_rate / freq
    pv = 0.0
    for t in range(1, periods + 1):
        pv += coupon / (1 + r) ** t
    pv += bond.face_value / (1 + r) ** periods
    return pv


def bond_dv01(bond: BondPosition, yield_rate: float) -> float:
    """PV sensitivity to a 1bp (0.0001) yield change."""
    pv_up = bond_pv(bond, yield_rate + 0.0001)
    pv_down = bond_pv(bond, yield_rate - 0.0001)
    return abs(pv_down - pv_up) / 2.0


def bond_modified_duration(bond: BondPosition, yield_rate: float) -> float:
    """Modified duration = DV01 * 10_000 / PV."""
    pv = bond_pv(bond, yield_rate)
    if pv == 0:
        return 0.0
    return bond_dv01(bond, yield_rate) * 10_000 / pv


def _years_to_maturity(bond: BondPosition) -> float:
    if not bond.maturity_date:
        return 0.0
    try:
        mat = date.fromisoformat(bond.maturity_date)
        return max(0.0, (mat - date.today()).days / 365.25)
    except ValueError:
        return 0.0
