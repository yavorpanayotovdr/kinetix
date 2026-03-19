"""Simplified swap PV and DV01 pricing."""
from datetime import date

from kinetix_risk.market_data_models import YieldCurveData
from kinetix_risk.models import SwapPosition


def swap_pv(swap: SwapPosition, yield_curve: YieldCurveData) -> float:
    """PV of a PAY_FIXED or RECEIVE_FIXED swap using a flat-discounting approximation.

    The floating leg is priced at par (its PV equals notional at inception),
    and the fixed leg is discounted as a series of fixed cash flows plus notional.
    PV = PV(float) - PV(fixed) for PAY_FIXED, reversed for RECEIVE_FIXED.
    """
    years = _years_to_maturity(swap)
    if years <= 0:
        return 0.0

    market_rate = yield_curve.interpolate(int(years * 365))
    freq = swap.fixed_frequency or 2
    periods = max(1, int(years * freq))
    r = market_rate / freq

    # PV of fixed leg (series of fixed coupons + notional repayment)
    fixed_coupon = swap.notional * swap.fixed_rate / freq
    pv_fixed = sum(fixed_coupon / (1 + r) ** t for t in range(1, periods + 1))
    pv_fixed += swap.notional / (1 + r) ** periods

    # Floating leg at par: at inception the PV of a floating-rate bond equals notional
    pv_float = swap.notional

    if swap.pay_receive == "PAY_FIXED":
        return pv_float - pv_fixed
    else:
        return pv_fixed - pv_float


def swap_dv01(swap: SwapPosition, yield_curve: YieldCurveData) -> float:
    """DV01: absolute PV change for a 1bp parallel shift of the yield curve."""
    pv_base = swap_pv(swap, yield_curve)
    shifted = yield_curve.shift(0.0001)
    pv_up = swap_pv(swap, shifted)
    return abs(pv_up - pv_base)


def _years_to_maturity(swap: SwapPosition) -> float:
    if not swap.maturity_date:
        return 0.0
    try:
        mat = date.fromisoformat(swap.maturity_date)
        return max(0.0, (mat - date.today()).days / 365.25)
    except ValueError:
        return 0.0
