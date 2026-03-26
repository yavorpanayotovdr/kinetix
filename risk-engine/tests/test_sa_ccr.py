"""Unit tests for SA-CCR (Standardised Approach for Counterparty Credit Risk, BCBS 279).

SA-CCR is the regulatory capital model for counterparty credit risk.  It is
explicitly distinct from the internal Monte Carlo PFE model — the two coexist
and serve different purposes:

  - SA-CCR  : regulatory capital (BCBS 279), deterministic formulaic approach
  - MC PFE  : internal economic model, Monte Carlo GBM simulation

These tests verify the core SA-CCR calculation engine in isolation.
"""
from __future__ import annotations

import math

import pytest

from kinetix_risk.models import (
    AssetClass,
    FxPosition,
    OptionPosition,
    OptionType,
    PositionRisk,
    SwapPosition,
)
from kinetix_risk.sa_ccr import (
    HedgingSetAddon,
    SaCcrResult,
    SaCcrTradeClassification,
    aggregate_hedging_sets,
    calculate_sa_ccr,
    classify_trade,
)

pytestmark = pytest.mark.unit

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _equity_position(
    instrument_id: str = "AAPL",
    market_value: float = 1_000_000.0,
    quantity: float = 1.0,
    currency: str = "USD",
) -> PositionRisk:
    return PositionRisk(
        instrument_id=instrument_id,
        asset_class=AssetClass.EQUITY,
        market_value=market_value,
        currency=currency,
    )


def _fx_position(
    instrument_id: str = "EURUSD",
    market_value: float = 1_000_000.0,
    base_currency: str = "EUR",
    quote_currency: str = "USD",
) -> FxPosition:
    return FxPosition(
        instrument_id=instrument_id,
        asset_class=AssetClass.FX,
        market_value=market_value,
        currency="USD",
        base_currency=base_currency,
        quote_currency=quote_currency,
    )


def _ir_swap(
    instrument_id: str = "SWAP-001",
    market_value: float = 100_000.0,
    notional: float = 10_000_000.0,
    maturity_date: str = "2030-01-01",
    pay_receive: str = "PAY_FIXED",
    currency: str = "USD",
) -> SwapPosition:
    return SwapPosition(
        instrument_id=instrument_id,
        asset_class=AssetClass.FIXED_INCOME,
        market_value=market_value,
        currency=currency,
        notional=notional,
        fixed_rate=0.03,
        float_index="SOFR",
        maturity_date=maturity_date,
        pay_receive=pay_receive,
    )


def _equity_option(
    instrument_id: str = "AAPL-CALL",
    market_value: float = 50_000.0,
    spot: float = 150.0,
    strike: float = 155.0,
    expiry_days: int = 90,
    option_type: OptionType = OptionType.CALL,
    quantity: float = 1000.0,
) -> OptionPosition:
    return OptionPosition(
        instrument_id=instrument_id,
        underlying_id="AAPL",
        option_type=option_type,
        strike=strike,
        expiry_days=expiry_days,
        spot_price=spot,
        implied_vol=0.30,
        risk_free_rate=0.05,
        quantity=quantity,
        asset_class=AssetClass.EQUITY,
    )


EMPTY_MARKET_DATA: dict = {}


# ---------------------------------------------------------------------------
# classify_trade — supervisory delta
# ---------------------------------------------------------------------------


class TestClassifyTradeSupervisoryDelta:
    def test_long_linear_equity_has_delta_plus_one(self):
        pos = _equity_position(market_value=1_000_000.0)
        classification = classify_trade(pos, EMPTY_MARKET_DATA)
        assert classification.supervisory_delta == pytest.approx(1.0)

    def test_short_linear_equity_has_delta_minus_one(self):
        pos = _equity_position(market_value=-1_000_000.0)
        classification = classify_trade(pos, EMPTY_MARKET_DATA)
        assert classification.supervisory_delta == pytest.approx(-1.0)

    def test_call_option_uses_bs_delta(self):
        opt = _equity_option(option_type=OptionType.CALL, spot=150.0, strike=155.0)
        classification = classify_trade(opt, EMPTY_MARKET_DATA)
        # Call delta is in (0, 1) for OTM call
        assert 0.0 < classification.supervisory_delta < 1.0

    def test_put_option_uses_bs_delta(self):
        opt = _equity_option(option_type=OptionType.PUT, spot=150.0, strike=145.0)
        classification = classify_trade(opt, EMPTY_MARKET_DATA)
        # Put delta is in (-1, 0) for OTM put
        assert -1.0 < classification.supervisory_delta < 0.0


# ---------------------------------------------------------------------------
# classify_trade — adjusted notional
# ---------------------------------------------------------------------------


class TestClassifyTradeAdjustedNotional:
    def test_equity_adjusted_notional_is_market_value(self):
        pos = _equity_position(market_value=1_000_000.0)
        classification = classify_trade(pos, EMPTY_MARKET_DATA)
        assert classification.adjusted_notional == pytest.approx(1_000_000.0)

    def test_fx_adjusted_notional_is_abs_market_value(self):
        pos = _fx_position(market_value=2_000_000.0)
        classification = classify_trade(pos, EMPTY_MARKET_DATA)
        assert classification.adjusted_notional == pytest.approx(2_000_000.0)

    def test_ir_swap_adjusted_notional_is_notional_field(self):
        swap = _ir_swap(notional=10_000_000.0)
        classification = classify_trade(swap, EMPTY_MARKET_DATA)
        assert classification.adjusted_notional == pytest.approx(10_000_000.0)


# ---------------------------------------------------------------------------
# classify_trade — supervisory factor
# ---------------------------------------------------------------------------


class TestClassifyTradeSupervisoryFactor:
    def test_equity_supervisory_factor(self):
        pos = _equity_position()
        classification = classify_trade(pos, EMPTY_MARKET_DATA)
        assert classification.supervisory_factor == pytest.approx(0.32)

    def test_fx_supervisory_factor(self):
        pos = _fx_position()
        classification = classify_trade(pos, EMPTY_MARKET_DATA)
        assert classification.supervisory_factor == pytest.approx(0.04)

    def test_ir_swap_supervisory_factor(self):
        swap = _ir_swap()
        classification = classify_trade(swap, EMPTY_MARKET_DATA)
        assert classification.supervisory_factor == pytest.approx(0.005)


# ---------------------------------------------------------------------------
# classify_trade — maturity factor
# ---------------------------------------------------------------------------


class TestClassifyTradeMaturityFactor:
    def test_maturity_factor_is_one_for_maturity_beyond_one_year(self):
        # M > 1 year → min(M, 1) = 1 → sqrt(1) = 1.0
        swap = _ir_swap(maturity_date="2028-06-01")  # well beyond 1 year from today
        classification = classify_trade(swap, EMPTY_MARKET_DATA)
        assert classification.maturity_factor == pytest.approx(1.0)

    def test_maturity_factor_capped_at_one_for_very_long_maturity(self):
        # Even for very long maturity, MF is capped at 1
        swap = _ir_swap(maturity_date="2050-01-01")
        classification = classify_trade(swap, EMPTY_MARKET_DATA)
        assert classification.maturity_factor == pytest.approx(1.0)


# ---------------------------------------------------------------------------
# classify_trade — hedging set key
# ---------------------------------------------------------------------------


class TestClassifyTradeHedgingSetKey:
    def test_ir_swap_hedging_set_key_is_currency(self):
        swap = _ir_swap(currency="USD")
        classification = classify_trade(swap, EMPTY_MARKET_DATA)
        assert classification.hedging_set == "USD"

    def test_fx_hedging_set_key_is_sorted_currency_pair(self):
        pos = _fx_position(base_currency="EUR", quote_currency="USD")
        classification = classify_trade(pos, EMPTY_MARKET_DATA)
        assert classification.hedging_set in ("EUR/USD", "USD/EUR")

    def test_equity_hedging_set_key_is_instrument_id(self):
        pos = _equity_position(instrument_id="AAPL")
        classification = classify_trade(pos, EMPTY_MARKET_DATA)
        assert classification.hedging_set == "AAPL"


# ---------------------------------------------------------------------------
# aggregate_hedging_sets
# ---------------------------------------------------------------------------


class TestAggregateHedgingSets:
    def test_single_long_ir_swap_addon(self):
        """Addon = SF × |D × AN × MF| = 0.005 × |1 × 10M × 1| = 50,000."""
        swap = _ir_swap(notional=10_000_000.0, market_value=100_000.0)
        classifications = [classify_trade(swap, EMPTY_MARKET_DATA)]
        addons = aggregate_hedging_sets(classifications)
        assert len(addons) == 1
        assert addons[0].addon_amount == pytest.approx(50_000.0, rel=1e-3)

    def test_two_long_ir_swaps_same_currency_addons_sum(self):
        """Two PAY_FIXED swaps in same currency: both delta=+1, addons sum."""
        swap1 = _ir_swap("SWAP-1", notional=10_000_000.0, currency="USD")
        swap2 = _ir_swap("SWAP-2", notional=5_000_000.0, currency="USD")
        classifications = [
            classify_trade(swap1, EMPTY_MARKET_DATA),
            classify_trade(swap2, EMPTY_MARKET_DATA),
        ]
        addons = aggregate_hedging_sets(classifications)
        # Both same hedging set: addon = SF × (D1×AN1 + D2×AN2) × MF = 0.005 × 15M = 75,000
        assert len(addons) == 1
        assert addons[0].addon_amount == pytest.approx(75_000.0, rel=1e-3)

    def test_offsetting_swaps_reduce_addon(self):
        """PAY_FIXED and RECEIVE_FIXED in same currency partially offset."""
        swap_pay = _ir_swap("SWAP-PAY", notional=10_000_000.0, pay_receive="PAY_FIXED", market_value=50_000.0)
        swap_recv = _ir_swap("SWAP-RECV", notional=10_000_000.0, pay_receive="RECEIVE_FIXED", market_value=-50_000.0)
        classifications = [
            classify_trade(swap_pay, EMPTY_MARKET_DATA),
            classify_trade(swap_recv, EMPTY_MARKET_DATA),
        ]
        addons = aggregate_hedging_sets(classifications)
        # Perfect offset → addon ~ 0
        assert len(addons) == 1
        assert addons[0].addon_amount == pytest.approx(0.0, abs=1.0)

    def test_two_different_currencies_create_separate_hedging_sets(self):
        swap_usd = _ir_swap("SWAP-USD", currency="USD")
        swap_eur = _ir_swap("SWAP-EUR", currency="EUR")
        classifications = [
            classify_trade(swap_usd, EMPTY_MARKET_DATA),
            classify_trade(swap_eur, EMPTY_MARKET_DATA),
        ]
        addons = aggregate_hedging_sets(classifications)
        assert len(addons) == 2

    def test_equity_and_ir_are_separate_asset_classes(self):
        equity = _equity_position()
        swap = _ir_swap()
        classifications = [
            classify_trade(equity, EMPTY_MARKET_DATA),
            classify_trade(swap, EMPTY_MARKET_DATA),
        ]
        addons = aggregate_hedging_sets(classifications)
        asset_classes = {a.asset_class for a in addons}
        assert len(asset_classes) == 2

    def test_empty_classifications_returns_empty_list(self):
        addons = aggregate_hedging_sets([])
        assert addons == []


# ---------------------------------------------------------------------------
# calculate_sa_ccr — RC (replacement cost)
# ---------------------------------------------------------------------------


class TestReplacementCost:
    def test_positive_mtm_with_no_collateral_rc_equals_mtm(self):
        swap = _ir_swap(market_value=500_000.0)
        result = calculate_sa_ccr([swap], EMPTY_MARKET_DATA, collateral_net=0.0)
        assert result.replacement_cost == pytest.approx(500_000.0)

    def test_over_collateralised_netting_set_rc_is_zero(self):
        """RC = max(V - C, 0) = max(500k - 600k, 0) = 0."""
        swap = _ir_swap(market_value=500_000.0)
        result = calculate_sa_ccr([swap], EMPTY_MARKET_DATA, collateral_net=600_000.0)
        assert result.replacement_cost == pytest.approx(0.0)

    def test_negative_mtm_netting_set_rc_is_zero(self):
        """RC = max(-200k - 0, 0) = 0."""
        swap = _ir_swap(market_value=-200_000.0)
        result = calculate_sa_ccr([swap], EMPTY_MARKET_DATA, collateral_net=0.0)
        assert result.replacement_cost == pytest.approx(0.0)


# ---------------------------------------------------------------------------
# calculate_sa_ccr — multiplier
# ---------------------------------------------------------------------------


class TestMultiplier:
    def test_multiplier_is_one_for_positive_mtm_no_collateral(self):
        """When V > 0 and C = 0, V/(2*0.05*AddOn) can be large → multiplier ≈ 1."""
        swap = _ir_swap(notional=10_000_000.0, market_value=1_000_000.0)
        result = calculate_sa_ccr([swap], EMPTY_MARKET_DATA, collateral_net=0.0)
        assert result.multiplier == pytest.approx(1.0, rel=0.01)

    def test_multiplier_floors_at_0_05_for_deeply_over_collateralised_set(self):
        """Floor at 5%: multiplier = min(1, 0.05 + 0.95 × exp(V/(2×0.05×AddOn)))."""
        # Large collateral drives V-C very negative → exp → 0 → multiplier → 0.05
        swap = _ir_swap(notional=10_000_000.0, market_value=100_000.0)
        # Massive over-collateralisation
        result = calculate_sa_ccr([swap], EMPTY_MARKET_DATA, collateral_net=100_000_000.0)
        assert result.multiplier >= 0.05
        assert result.multiplier <= 1.0


# ---------------------------------------------------------------------------
# calculate_sa_ccr — EAD
# ---------------------------------------------------------------------------


class TestEad:
    def test_ead_formula_1_4_times_rc_plus_pfe(self):
        swap = _ir_swap(notional=10_000_000.0, market_value=100_000.0)
        result = calculate_sa_ccr([swap], EMPTY_MARKET_DATA, collateral_net=0.0)
        expected_ead = 1.4 * (result.replacement_cost + result.pfe_addon)
        assert result.ead == pytest.approx(expected_ead, rel=1e-9)

    def test_alpha_constant_is_1_4(self):
        swap = _ir_swap()
        result = calculate_sa_ccr([swap], EMPTY_MARKET_DATA, collateral_net=0.0)
        assert result.alpha == pytest.approx(1.4)


# ---------------------------------------------------------------------------
# calculate_sa_ccr — edge cases
# ---------------------------------------------------------------------------


class TestEdgeCases:
    def test_zero_positions_returns_zero_ead(self):
        result = calculate_sa_ccr([], EMPTY_MARKET_DATA, collateral_net=0.0)
        assert result.ead == pytest.approx(0.0)
        assert result.replacement_cost == pytest.approx(0.0)
        assert result.pfe_addon == pytest.approx(0.0)

    def test_over_collateralised_netting_set_reduces_ead(self):
        """With over-collateralisation, RC = 0 and multiplier < 1, so EAD < 1.4 × AddOn."""
        swap = _ir_swap(notional=10_000_000.0, market_value=100_000.0)
        result_uncollateralised = calculate_sa_ccr([swap], EMPTY_MARKET_DATA, collateral_net=0.0)
        result_over_collateralised = calculate_sa_ccr([swap], EMPTY_MARKET_DATA, collateral_net=10_000_000.0)
        assert result_over_collateralised.ead < result_uncollateralised.ead

    def test_sa_ccr_result_labels_are_distinct_from_mc_pfe(self):
        """SA-CCR must not be confused with internal MC PFE in the result type."""
        swap = _ir_swap()
        result = calculate_sa_ccr([swap], EMPTY_MARKET_DATA, collateral_net=0.0)
        # pfe_addon is the SA-CCR regulatory add-on, not the MC GBM PFE
        assert isinstance(result, SaCcrResult)
        assert hasattr(result, "pfe_addon")   # SA-CCR regulatory add-on
        assert hasattr(result, "replacement_cost")
        assert hasattr(result, "ead")


# ---------------------------------------------------------------------------
# Credit subtype supervisory factor tests (BCBS 279 Table 2)
# ---------------------------------------------------------------------------

class TestCreditSubtypeSupervisoryFactor:
    """Verify CREDIT_IG and CREDIT_HY positions use the correct supervisory
    factors (0.0038 and 0.05) instead of the IR factor (0.005)."""

    def test_credit_ig_uses_correct_supervisory_factor(self):
        """CREDIT_IG SF = 0.0038, not IR SF = 0.005."""
        from kinetix_risk.sa_ccr import classify_trade
        pos = PositionRisk(
            instrument_id="CDS-IG-001",
            asset_class=AssetClass.FIXED_INCOME,
            market_value=100_000.0,
            currency="USD",
            credit_subtype="CREDIT_IG",
        )
        classification = classify_trade(pos, None)
        assert classification.supervisory_factor == 0.0038, (
            f"CREDIT_IG should use SF=0.0038, got {classification.supervisory_factor}"
        )
        assert classification.asset_class == "CREDIT_IG"

    def test_credit_hy_uses_correct_supervisory_factor(self):
        """CREDIT_HY SF = 0.05, which is 10x the IR SF of 0.005."""
        from kinetix_risk.sa_ccr import classify_trade
        pos = PositionRisk(
            instrument_id="CDS-HY-001",
            asset_class=AssetClass.FIXED_INCOME,
            market_value=100_000.0,
            currency="USD",
            credit_subtype="CREDIT_HY",
        )
        classification = classify_trade(pos, None)
        assert classification.supervisory_factor == 0.05, (
            f"CREDIT_HY should use SF=0.05, got {classification.supervisory_factor}"
        )
        assert classification.asset_class == "CREDIT_HY"

    def test_ir_position_without_credit_subtype_uses_ir_factor(self):
        """A plain FIXED_INCOME position without credit_subtype uses IR SF=0.005."""
        from kinetix_risk.sa_ccr import classify_trade
        pos = PositionRisk(
            instrument_id="SWAP-001",
            asset_class=AssetClass.FIXED_INCOME,
            market_value=100_000.0,
            currency="USD",
        )
        classification = classify_trade(pos, None)
        assert classification.supervisory_factor == 0.005
        assert classification.asset_class == "IR"
