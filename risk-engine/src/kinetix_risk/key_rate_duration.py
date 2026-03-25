"""Key Rate Duration (KRD) calculation via per-tenor analytical DV01.

Each tenor bucket applies a +1bp tent function bump to the yield curve,
reprices the bond using full DCF discounting at per-cashflow curve rates,
and computes the price sensitivity (DV01) for that tenor.
"""
from dataclasses import dataclass

from kinetix_risk.market_data_models import YieldCurveData

# Standard 4-bucket tenor set: (label, centre_days, half_width_days)
STANDARD_TENOR_BUCKETS: list[tuple[str, int]] = [
    ("2Y", 730),
    ("5Y", 1825),
    ("10Y", 3650),
    ("30Y", 10950),
]

# Half-width of each tent in days — sized so adjacent tents just touch
_TENOR_HALF_WIDTHS: dict[int, int] = {
    730: 730,    # 2Y bucket spans [0d, 1460d]
    1825: 1095,  # 5Y bucket spans [730d, 2920d]
    3650: 1825,  # 10Y bucket spans [1825d, 5475d]
    10950: 7300, # 30Y bucket spans [3650d, 18250d]
}

ONE_BPS = 0.0001


@dataclass(frozen=True)
class KeyRateDuration:
    tenor_label: str
    tenor_days: int
    dv01: float


@dataclass(frozen=True)
class KeyRateDurationResult:
    krd_buckets: list[KeyRateDuration]
    total_dv01: float


def bond_pv_curve(
    face_value: float,
    coupon_rate: float,
    coupon_frequency: int,
    maturity_years: float,
    yield_curve: YieldCurveData,
) -> float:
    """Discount each cash flow at the curve rate for its maturity.

    Each coupon payment and the final principal redemption are discounted
    using the yield curve rate interpolated at the payment date.
    """
    if maturity_years <= 0:
        return face_value

    freq = coupon_frequency or 2
    coupon = face_value * coupon_rate / freq
    periods = int(maturity_years * freq)
    if periods <= 0:
        periods = 1

    years_per_period = 1.0 / freq
    pv = 0.0
    for t in range(1, periods + 1):
        payment_years = t * years_per_period
        payment_days = int(payment_years * 365.25)
        r = yield_curve.rate_at(payment_days) / freq
        cashflow = coupon
        if t == periods:
            cashflow += face_value
        pv += cashflow / (1.0 + r) ** t

    return pv


def calculate_krd(
    face_value: float,
    coupon_rate: float,
    coupon_frequency: int,
    maturity_years: float,
    yield_curve: YieldCurveData,
    tenor_buckets: list[tuple[str, int]],
) -> KeyRateDurationResult:
    """Compute key rate DV01 for each tenor bucket.

    For each bucket:
    1. Apply a +1bp tent function bump centred at the bucket tenor.
    2. Reprice the bond on the bumped curve.
    3. DV01 = PV(base) - PV(bumped)  [positive means price falls when rate rises]

    Returns a KeyRateDurationResult with per-bucket DV01s and the total.
    """
    pv_base = bond_pv_curve(face_value, coupon_rate, coupon_frequency, maturity_years, yield_curve)

    buckets: list[KeyRateDuration] = []
    for label, centre_days in tenor_buckets:
        width = _TENOR_HALF_WIDTHS.get(centre_days, centre_days)
        bumped_curve = yield_curve.partial_shift(
            tenor_days=centre_days,
            bump_bps=ONE_BPS,
            width_days=width,
        )
        pv_bumped = bond_pv_curve(
            face_value, coupon_rate, coupon_frequency, maturity_years, bumped_curve
        )
        dv01 = pv_base - pv_bumped
        buckets.append(KeyRateDuration(tenor_label=label, tenor_days=centre_days, dv01=dv01))

    total_dv01 = sum(b.dv01 for b in buckets)
    return KeyRateDurationResult(krd_buckets=buckets, total_dv01=total_dv01)
