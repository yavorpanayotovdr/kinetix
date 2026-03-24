"""Unit tests for credit_exposure.py — PFE, CVA, wrong-way risk detection.

All tests use synthetic data and run in-process.  No gRPC, no DB.
"""
import math

import numpy as np
import pytest

from kinetix_risk.credit_exposure import (
    CVAResult,
    ExposureProfile,
    PFEResult,
    PositionExposure,
    WrongWayRiskFlag,
    _cds_to_hazard_rate,
    _discount_factor,
    _pd_from_hazard,
    _rating_to_pd,
    calculate_cva,
    calculate_pfe,
    detect_wrong_way_risk,
)

pytestmark = pytest.mark.unit


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


def _equity_position(
    instrument_id: str = "AAPL",
    market_value: float = 1_000_000.0,
    vol: float = 0.25,
    sector: str = "TECHNOLOGY",
) -> PositionExposure:
    return PositionExposure(
        instrument_id=instrument_id,
        market_value=market_value,
        asset_class="EQUITY",
        volatility=vol,
        sector=sector,
    )


def _sample_exposure_profile() -> list[ExposureProfile]:
    """Minimal 2-tenor profile for CVA tests."""
    return [
        ExposureProfile("1Y", 1.0, expected_exposure=500_000.0, pfe_95=750_000.0, pfe_99=900_000.0),
        ExposureProfile("5Y", 5.0, expected_exposure=300_000.0, pfe_95=500_000.0, pfe_99=620_000.0),
    ]


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


class TestRatingToPd:
    def test_aaa_has_very_low_pd(self):
        pd = _rating_to_pd("AAA")
        assert pd < 0.001

    def test_ccc_has_high_pd(self):
        pd = _rating_to_pd("CCC")
        assert pd > 0.10

    def test_none_returns_unrated_pd(self):
        assert _rating_to_pd(None) == _rating_to_pd("UNRATED")

    def test_unknown_rating_returns_unrated_pd(self):
        assert _rating_to_pd("ZZZ") == _rating_to_pd("UNRATED")

    def test_case_insensitive(self):
        assert _rating_to_pd("bbb") == _rating_to_pd("BBB")

    def test_investment_grade_below_speculative_grade(self):
        assert _rating_to_pd("BBB") < _rating_to_pd("BB")


class TestCdsToHazardRate:
    def test_zero_lgd_raises(self):
        with pytest.raises(ValueError, match="LGD must be positive"):
            _cds_to_hazard_rate(100.0, 0.0)

    def test_100bps_with_40pct_lgd(self):
        h = _cds_to_hazard_rate(100.0, 0.40)
        assert abs(h - 0.025) < 1e-9

    def test_higher_spread_gives_higher_hazard(self):
        h_low = _cds_to_hazard_rate(50.0, 0.40)
        h_high = _cds_to_hazard_rate(200.0, 0.40)
        assert h_high > h_low


class TestPdFromHazard:
    def test_zero_tenor_returns_zero(self):
        assert _pd_from_hazard(0.05, 0.0) == 0.0

    def test_negative_tenor_returns_zero(self):
        assert _pd_from_hazard(0.05, -1.0) == 0.0

    def test_pd_increases_with_time(self):
        h = 0.02
        assert _pd_from_hazard(h, 1.0) < _pd_from_hazard(h, 5.0)

    def test_very_high_hazard_approaches_one(self):
        pd = _pd_from_hazard(100.0, 1.0)
        assert pd > 0.999


class TestDiscountFactor:
    def test_zero_rate_returns_one(self):
        assert _discount_factor(0.0, 1.0) == 1.0

    def test_positive_rate_less_than_one(self):
        assert _discount_factor(0.05, 1.0) < 1.0

    def test_longer_tenor_lower_discount(self):
        df_1y = _discount_factor(0.05, 1.0)
        df_5y = _discount_factor(0.05, 5.0)
        assert df_5y < df_1y


# ---------------------------------------------------------------------------
# PFE tests
# ---------------------------------------------------------------------------


class TestCalculatePfe:
    def test_empty_positions_returns_zero_exposure(self):
        result = calculate_pfe(
            counterparty_id="CP-001",
            netting_set_id="NS-001",
            agreement_type="ISDA_2002",
            positions=[],
        )
        assert isinstance(result, PFEResult)
        assert result.gross_exposure == 0.0
        assert result.net_exposure == 0.0
        assert all(ep.expected_exposure == 0.0 for ep in result.exposure_profile)

    def test_returns_one_profile_per_tenor(self):
        positions = [_equity_position()]
        result = calculate_pfe(
            counterparty_id="CP-001",
            netting_set_id="NS-001",
            agreement_type="ISDA_2002",
            positions=positions,
        )
        # Default 6 tenors
        assert len(result.exposure_profile) == 6
        tenors = [ep.tenor for ep in result.exposure_profile]
        assert "1M" in tenors
        assert "1Y" in tenors
        assert "5Y" in tenors

    def test_pfe_99_at_least_pfe_95(self):
        positions = [_equity_position()]
        result = calculate_pfe(
            counterparty_id="CP-001",
            netting_set_id="NS-001",
            agreement_type="ISDA_2002",
            positions=positions,
        )
        for ep in result.exposure_profile:
            assert ep.pfe_99 >= ep.pfe_95

    def test_pfe_95_exceeds_expected_exposure(self):
        """95th-percentile exposure should be >= mean positive exposure."""
        positions = [_equity_position(market_value=5_000_000.0, vol=0.30)]
        result = calculate_pfe(
            counterparty_id="CP-001",
            netting_set_id="NS-001",
            agreement_type="ISDA_2002",
            positions=positions,
            num_simulations=50_000,
        )
        # Skip 1M tenor (very short, EPE may be near zero)
        for ep in result.exposure_profile:
            assert ep.pfe_95 >= ep.expected_exposure

    def test_positive_market_value_produces_positive_exposure(self):
        positions = [_equity_position(market_value=2_000_000.0)]
        result = calculate_pfe(
            counterparty_id="CP-001",
            netting_set_id="NS-001",
            agreement_type="ISDA_2002",
            positions=positions,
        )
        one_year = next(ep for ep in result.exposure_profile if ep.tenor == "1Y")
        assert one_year.expected_exposure > 0.0

    def test_netting_reduces_exposure_versus_gross(self):
        """Long and offsetting short within one netting set should show benefit."""
        long_pos = _equity_position("AAPL", market_value=1_000_000.0)
        short_pos = _equity_position("AAPL_S", market_value=-900_000.0)
        result = calculate_pfe(
            counterparty_id="CP-001",
            netting_set_id="NS-001",
            agreement_type="ISDA_2002",
            positions=[long_pos, short_pos],
        )
        assert result.gross_exposure == pytest.approx(1_900_000.0)
        assert result.net_exposure == pytest.approx(100_000.0)

    def test_deterministic_with_seed(self):
        positions = [_equity_position()]
        r1 = calculate_pfe("CP-001", "NS-001", "ISDA_2002", positions, seed=99)
        r2 = calculate_pfe("CP-001", "NS-001", "ISDA_2002", positions, seed=99)
        for ep1, ep2 in zip(r1.exposure_profile, r2.exposure_profile):
            assert ep1.pfe_95 == pytest.approx(ep2.pfe_95)

    def test_different_seeds_produce_different_results(self):
        positions = [_equity_position(vol=0.30)]
        r1 = calculate_pfe("CP-001", "NS-001", "ISDA_2002", positions, seed=1)
        r2 = calculate_pfe("CP-001", "NS-001", "ISDA_2002", positions, seed=2)
        # At least one tenor should differ
        any_different = any(
            abs(ep1.pfe_95 - ep2.pfe_95) > 0
            for ep1, ep2 in zip(r1.exposure_profile, r2.exposure_profile)
        )
        assert any_different

    def test_higher_vol_produces_higher_pfe(self):
        low_vol = _equity_position(vol=0.10)
        high_vol = _equity_position(vol=0.50)
        r_low = calculate_pfe("CP-001", "NS-001", "ISDA_2002", [low_vol], num_simulations=50_000, seed=42)
        r_high = calculate_pfe("CP-001", "NS-001", "ISDA_2002", [high_vol], num_simulations=50_000, seed=42)
        one_y_low = next(ep.pfe_95 for ep in r_low.exposure_profile if ep.tenor == "1Y")
        one_y_high = next(ep.pfe_95 for ep in r_high.exposure_profile if ep.tenor == "1Y")
        assert one_y_high > one_y_low

    def test_invalid_correlation_matrix_repaired(self):
        """A near-singular correlation matrix should be repaired silently."""
        positions = [_equity_position("A"), _equity_position("B")]
        bad_corr = np.array([[1.0, 1.01], [1.01, 1.0]])  # not PSD
        # Should not raise
        result = calculate_pfe(
            "CP-001", "NS-001", "ISDA_2002",
            positions=positions,
            correlation_matrix=bad_corr,
        )
        assert len(result.exposure_profile) == 6

    def test_counterparty_id_propagated(self):
        result = calculate_pfe("COUNTERPARTY-XYZ", "NS-001", "ISDA_2002", [_equity_position()])
        assert result.counterparty_id == "COUNTERPARTY-XYZ"

    def test_netting_set_id_propagated(self):
        result = calculate_pfe("CP-001", "NS-TRADE-BANK", "ISDA_2002", [_equity_position()])
        assert result.netting_set_id == "NS-TRADE-BANK"


# ---------------------------------------------------------------------------
# CVA tests
# ---------------------------------------------------------------------------


class TestCalculateCva:
    def test_zero_exposure_gives_zero_cva(self):
        result = calculate_cva(
            counterparty_id="CP-001",
            exposure_profile=[],
            lgd=0.40,
            pd_1y=0.01,
        )
        assert result.cva == 0.0

    def test_positive_pd_and_exposure_gives_positive_cva(self):
        profile = _sample_exposure_profile()
        result = calculate_cva(
            counterparty_id="CP-001",
            exposure_profile=profile,
            lgd=0.40,
            pd_1y=0.02,
        )
        assert result.cva > 0.0

    def test_higher_pd_gives_higher_cva(self):
        profile = _sample_exposure_profile()
        r_low = calculate_cva("CP-001", profile, lgd=0.40, pd_1y=0.005)
        r_high = calculate_cva("CP-001", profile, lgd=0.40, pd_1y=0.05)
        assert r_high.cva > r_low.cva

    def test_higher_lgd_gives_higher_cva(self):
        profile = _sample_exposure_profile()
        r_low = calculate_cva("CP-001", profile, lgd=0.20, pd_1y=0.02)
        r_high = calculate_cva("CP-001", profile, lgd=0.70, pd_1y=0.02)
        assert r_high.cva > r_low.cva

    def test_cds_spread_produces_valid_cva(self):
        profile = _sample_exposure_profile()
        result = calculate_cva(
            counterparty_id="CP-001",
            exposure_profile=profile,
            lgd=0.40,
            cds_spread_bps=100.0,
        )
        assert result.cva > 0.0
        assert not result.is_estimated

    def test_rating_based_cva_not_estimated(self):
        profile = _sample_exposure_profile()
        result = calculate_cva(
            counterparty_id="CP-001",
            exposure_profile=profile,
            lgd=0.40,
            rating="BBB",
        )
        assert result.cva > 0.0
        assert not result.is_estimated

    def test_no_credit_data_uses_sector_average_and_flags_estimated(self):
        profile = _sample_exposure_profile()
        result = calculate_cva(
            counterparty_id="CP-001",
            exposure_profile=profile,
            lgd=0.40,
            sector="FINANCIALS",
        )
        assert result.cva > 0.0
        assert result.is_estimated is True

    def test_aaa_counterparty_has_very_low_cva(self):
        profile = _sample_exposure_profile()
        result_aaa = calculate_cva("CP-001", profile, lgd=0.40, rating="AAA")
        result_ccc = calculate_cva("CP-001", profile, lgd=0.40, rating="CCC")
        assert result_aaa.cva < result_ccc.cva

    def test_invalid_lgd_raises(self):
        profile = _sample_exposure_profile()
        with pytest.raises(ValueError, match="LGD must be in"):
            calculate_cva("CP-001", profile, lgd=0.0)
        with pytest.raises(ValueError, match="LGD must be in"):
            calculate_cva("CP-001", profile, lgd=1.5)

    def test_pd_1y_takes_priority_over_cds(self):
        profile = _sample_exposure_profile()
        r_pd = calculate_cva("CP-001", profile, lgd=0.40, pd_1y=0.001)
        r_cds = calculate_cva("CP-001", profile, lgd=0.40, cds_spread_bps=1000.0)
        # pd_1y=0.001 is much lower than what 1000bps CDS implies
        assert r_pd.cva < r_cds.cva

    def test_counterparty_id_propagated(self):
        result = calculate_cva("MY-CP", _sample_exposure_profile(), lgd=0.40, pd_1y=0.01)
        assert result.counterparty_id == "MY-CP"

    def test_pd_1y_in_result_matches_input_approximately(self):
        profile = _sample_exposure_profile()
        result = calculate_cva("CP-001", profile, lgd=0.40, pd_1y=0.05)
        assert abs(result.pd_1y - 0.05) < 0.01


# ---------------------------------------------------------------------------
# Wrong-way risk tests
# ---------------------------------------------------------------------------


class TestDetectWrongWayRisk:
    def test_matching_sector_flagged(self):
        positions = [_equity_position("JPM", sector="FINANCIALS")]
        flags = detect_wrong_way_risk("FINANCIALS", positions)
        assert len(flags) == 1
        assert flags[0].instrument_id == "JPM"

    def test_non_matching_sector_not_flagged(self):
        positions = [_equity_position("AAPL", sector="TECHNOLOGY")]
        flags = detect_wrong_way_risk("FINANCIALS", positions)
        assert len(flags) == 0

    def test_multiple_positions_only_matching_flagged(self):
        positions = [
            _equity_position("JPM", sector="FINANCIALS"),
            _equity_position("AAPL", sector="TECHNOLOGY"),
            _equity_position("GS", sector="FINANCIALS"),
        ]
        flags = detect_wrong_way_risk("FINANCIALS", positions)
        flagged_ids = [f.instrument_id for f in flags]
        assert "JPM" in flagged_ids
        assert "GS" in flagged_ids
        assert "AAPL" not in flagged_ids

    def test_zero_market_value_not_flagged(self):
        positions = [_equity_position("JPM", market_value=0.0, sector="FINANCIALS")]
        flags = detect_wrong_way_risk("FINANCIALS", positions)
        assert len(flags) == 0

    def test_flag_contains_sector_info(self):
        positions = [_equity_position("GS", sector="FINANCIALS", market_value=500_000.0)]
        flags = detect_wrong_way_risk("FINANCIALS", positions)
        assert flags[0].counterparty_sector == "FINANCIALS"
        assert flags[0].position_sector == "FINANCIALS"
        assert flags[0].exposure == pytest.approx(500_000.0)

    def test_case_insensitive_sector_matching(self):
        positions = [_equity_position("GS", sector="financials")]
        flags = detect_wrong_way_risk("FINANCIALS", positions)
        assert len(flags) == 1

    def test_empty_positions_returns_empty(self):
        flags = detect_wrong_way_risk("FINANCIALS", [])
        assert flags == []
