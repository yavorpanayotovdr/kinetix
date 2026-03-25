"""Unit tests for key rate duration calculation.

These tests verify:
- tent function bump via partial_shift
- KRD for a known bond concentrates DV01 near the maturity bucket
- KRD buckets sum to approximately the full-curve DV01
"""
import pytest

from kinetix_risk.market_data_models import YieldCurveData
from kinetix_risk.key_rate_duration import (
    STANDARD_TENOR_BUCKETS,
    KeyRateDuration,
    KeyRateDurationResult,
    calculate_krd,
)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

def _flat_curve(rate: float = 0.05) -> YieldCurveData:
    """Yield curve flat at *rate* across the standard tenor nodes."""
    return YieldCurveData(tenors=[
        (365, rate),
        (730, rate),
        (1825, rate),
        (3650, rate),
        (10950, rate),
    ])


def _upward_curve() -> YieldCurveData:
    """A realistic upward-sloping yield curve."""
    return YieldCurveData(tenors=[
        (365, 0.04),
        (730, 0.045),
        (1825, 0.05),
        (3650, 0.055),
        (10950, 0.06),
    ])


# ---------------------------------------------------------------------------
# YieldCurveData.partial_shift — tent function
# ---------------------------------------------------------------------------

class TestPartialShift:
    @pytest.mark.unit
    def test_rate_at_bump_centre_increases_by_full_bps(self):
        curve = _flat_curve(0.05)
        bumped = curve.partial_shift(tenor_days=3650, bump_bps=0.0001, width_days=1825)
        assert abs(bumped.rate_at(3650) - (0.05 + 0.0001)) < 1e-10

    @pytest.mark.unit
    def test_rates_outside_tent_are_unchanged(self):
        curve = _flat_curve(0.05)
        bumped = curve.partial_shift(tenor_days=3650, bump_bps=0.0001, width_days=1825)
        # 365-day tenor is well outside the tent (3650 - 1825 = 1825, so 365 is outside)
        assert abs(bumped.rate_at(365) - 0.05) < 1e-10

    @pytest.mark.unit
    def test_rate_at_tent_edge_node_is_unchanged(self):
        curve = _flat_curve(0.05)
        bumped = curve.partial_shift(tenor_days=3650, bump_bps=0.0001, width_days=1825)
        # Left edge of tent is at 1825 days — that tenor node is exactly at the edge (weight=0)
        assert abs(bumped.rate_at(1825) - 0.05) < 1e-10
        # The 10950-day node is well outside the right edge (5475 days) so it is unchanged
        assert abs(bumped.rate_at(10950) - 0.05) < 1e-10

    @pytest.mark.unit
    def test_rate_at_tent_midpoint_is_half_bump(self):
        """Midpoint between edge and centre should get half the bump."""
        curve = _flat_curve(0.05)
        # Centre at 3650, width 1825 => left edge at 1825, right edge at 5475
        # Midpoint on left side: (1825 + 3650) / 2 = 2737
        bumped = curve.partial_shift(tenor_days=3650, bump_bps=0.0001, width_days=1825)
        # Linear interpolation: at 2737 (halfway between 1825 and 3650), bump is 0.5 * 0.0001
        mid_rate = bumped.rate_at(2737)
        assert abs(mid_rate - (0.05 + 0.5 * 0.0001)) < 1e-6

    @pytest.mark.unit
    def test_partial_shift_returns_new_curve_leaving_original_unchanged(self):
        curve = _flat_curve(0.05)
        bumped = curve.partial_shift(tenor_days=3650, bump_bps=0.0001, width_days=1825)
        assert bumped is not curve
        assert abs(curve.rate_at(3650) - 0.05) < 1e-10

    @pytest.mark.unit
    def test_partial_shift_on_existing_tenors_only(self):
        """partial_shift only adjusts rates at tenor nodes that fall inside the tent."""
        curve = _flat_curve(0.05)
        bumped = curve.partial_shift(tenor_days=1825, bump_bps=0.0001, width_days=730)
        # Only the 1825-day tenor node sits at the centre; 730 and 2555 are the edges
        # The 730 tenor node is exactly at the left edge => zero bump
        assert abs(bumped.rate_at(730) - 0.05) < 1e-10
        assert abs(bumped.rate_at(1825) - (0.05 + 0.0001)) < 1e-10


# ---------------------------------------------------------------------------
# bond_pv_curve
# ---------------------------------------------------------------------------

class TestBondPvCurve:
    @pytest.mark.unit
    def test_pv_with_flat_curve_matches_bond_pv_at_same_yield(self):
        """When curve is flat, bond_pv_curve should equal bond_pv at the flat rate."""
        from kinetix_risk.bond_pricing import bond_pv
        from kinetix_risk.key_rate_duration import bond_pv_curve
        from kinetix_risk.models import AssetClass, BondPosition

        bond = BondPosition(
            instrument_id="UST-10Y",
            asset_class=AssetClass.FIXED_INCOME,
            market_value=1_000_000.0,
            currency="USD",
            face_value=1_000_000.0,
            coupon_rate=0.05,
            coupon_frequency=2,
            maturity_date="2036-03-25",
        )
        flat_rate = 0.05
        curve = _flat_curve(flat_rate)

        pv_flat = bond_pv(bond, flat_rate)
        pv_curve = bond_pv_curve(
            face_value=bond.face_value,
            coupon_rate=bond.coupon_rate,
            coupon_frequency=bond.coupon_frequency,
            maturity_years=10.0,
            yield_curve=curve,
        )

        # Should agree within $10 on a $1M face value bond
        assert abs(pv_flat - pv_curve) < 10.0

    @pytest.mark.unit
    def test_pv_with_upward_curve_differs_from_flat_pv(self):
        from kinetix_risk.key_rate_duration import bond_pv_curve

        flat_curve = _flat_curve(0.05)
        upward_curve = _upward_curve()

        pv_flat = bond_pv_curve(
            face_value=1_000_000.0,
            coupon_rate=0.05,
            coupon_frequency=2,
            maturity_years=10.0,
            yield_curve=flat_curve,
        )
        pv_upward = bond_pv_curve(
            face_value=1_000_000.0,
            coupon_rate=0.05,
            coupon_frequency=2,
            maturity_years=10.0,
            yield_curve=upward_curve,
        )
        assert abs(pv_flat - pv_upward) > 100.0


# ---------------------------------------------------------------------------
# calculate_krd
# ---------------------------------------------------------------------------

class TestCalculateKrd:
    @pytest.mark.unit
    def test_returns_krd_result_with_four_buckets(self):
        result = calculate_krd(
            face_value=1_000_000.0,
            coupon_rate=0.05,
            coupon_frequency=2,
            maturity_years=10.0,
            yield_curve=_flat_curve(),
            tenor_buckets=STANDARD_TENOR_BUCKETS,
        )
        assert isinstance(result, KeyRateDurationResult)
        assert len(result.krd_buckets) == 4

    @pytest.mark.unit
    def test_bucket_labels_match_standard_tenors(self):
        result = calculate_krd(
            face_value=1_000_000.0,
            coupon_rate=0.05,
            coupon_frequency=2,
            maturity_years=10.0,
            yield_curve=_flat_curve(),
            tenor_buckets=STANDARD_TENOR_BUCKETS,
        )
        labels = [b.tenor_label for b in result.krd_buckets]
        assert labels == ["2Y", "5Y", "10Y", "30Y"]

    @pytest.mark.unit
    def test_10y_bond_has_most_dv01_in_10y_bucket(self):
        """A 10-year bond should have the largest key rate DV01 at the 10Y tenor."""
        result = calculate_krd(
            face_value=1_000_000.0,
            coupon_rate=0.05,
            coupon_frequency=2,
            maturity_years=10.0,
            yield_curve=_flat_curve(),
            tenor_buckets=STANDARD_TENOR_BUCKETS,
        )
        bucket_by_label = {b.tenor_label: b for b in result.krd_buckets}
        ten_y_dv01 = bucket_by_label["10Y"].dv01
        two_y_dv01 = bucket_by_label["2Y"].dv01
        five_y_dv01 = bucket_by_label["5Y"].dv01
        thirty_y_dv01 = bucket_by_label["30Y"].dv01

        assert ten_y_dv01 > two_y_dv01
        assert ten_y_dv01 > five_y_dv01
        assert ten_y_dv01 > thirty_y_dv01

    @pytest.mark.unit
    def test_krd_buckets_sum_approximately_equals_full_curve_dv01(self):
        """The sum of per-bucket DV01s should be close to the single full-curve DV01."""
        from kinetix_risk.key_rate_duration import bond_pv_curve

        curve = _flat_curve(0.05)
        face = 1_000_000.0
        coupon = 0.05
        freq = 2
        years = 10.0

        result = calculate_krd(
            face_value=face,
            coupon_rate=coupon,
            coupon_frequency=freq,
            maturity_years=years,
            yield_curve=curve,
            tenor_buckets=STANDARD_TENOR_BUCKETS,
        )

        # Full DV01 via parallel shift
        bump = 0.0001
        pv_base = bond_pv_curve(face, coupon, freq, years, curve)
        pv_up = bond_pv_curve(face, coupon, freq, years, curve.shift(bump))
        full_dv01 = abs(pv_up - pv_base)

        bucket_sum = sum(abs(b.dv01) for b in result.krd_buckets)

        # The tent function sum should approximately equal the parallel-shift DV01
        # Allow 20% tolerance because tent widths don't perfectly tile the curve
        assert abs(bucket_sum - full_dv01) / full_dv01 < 0.20

    @pytest.mark.unit
    def test_total_dv01_field_equals_sum_of_bucket_dv01s(self):
        result = calculate_krd(
            face_value=1_000_000.0,
            coupon_rate=0.05,
            coupon_frequency=2,
            maturity_years=10.0,
            yield_curve=_flat_curve(),
            tenor_buckets=STANDARD_TENOR_BUCKETS,
        )
        expected_total = sum(b.dv01 for b in result.krd_buckets)
        assert abs(result.total_dv01 - expected_total) < 1e-10

    @pytest.mark.unit
    def test_all_bucket_dv01s_are_positive_for_long_bond(self):
        """A long bond position should show positive DV01 sensitivity at every bucket."""
        result = calculate_krd(
            face_value=1_000_000.0,
            coupon_rate=0.05,
            coupon_frequency=2,
            maturity_years=10.0,
            yield_curve=_flat_curve(),
            tenor_buckets=STANDARD_TENOR_BUCKETS,
        )
        for bucket in result.krd_buckets:
            assert bucket.dv01 >= 0.0, f"Negative DV01 in bucket {bucket.tenor_label}: {bucket.dv01}"

    @pytest.mark.unit
    def test_short_bond_concentrates_dv01_in_2y_bucket(self):
        """A 2-year bond should have its dominant DV01 in the 2Y bucket."""
        result = calculate_krd(
            face_value=1_000_000.0,
            coupon_rate=0.04,
            coupon_frequency=2,
            maturity_years=2.0,
            yield_curve=_flat_curve(0.04),
            tenor_buckets=STANDARD_TENOR_BUCKETS,
        )
        bucket_by_label = {b.tenor_label: b for b in result.krd_buckets}
        two_y_dv01 = bucket_by_label["2Y"].dv01
        ten_y_dv01 = bucket_by_label["10Y"].dv01
        assert two_y_dv01 > ten_y_dv01
