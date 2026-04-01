"""Tests for position_resolver — delta-adjusted VaR for options and DV01-based for swaps."""

import pytest

from kinetix_risk.black_scholes import bs_delta
from kinetix_risk.market_data_consumer import MarketDataBundle
from kinetix_risk.market_data_models import YieldCurveData
from kinetix_risk.models import (
    AssetClass,
    BondPosition,
    FuturePosition,
    OptionPosition,
    OptionType,
    PositionRisk,
    SwapPosition,
)
from kinetix_risk.position_resolver import resolve_positions


@pytest.mark.unit
class TestResolvePositionsNonOption:
    def test_plain_position_risk_passes_through(self):
        pos = PositionRisk("AAPL", AssetClass.EQUITY, 1_000_000.0, "USD")
        result = resolve_positions([pos])
        assert len(result) == 1
        assert result[0] is pos

    def test_bond_position_passes_through(self):
        pos = BondPosition("US10Y", AssetClass.FIXED_INCOME, 980_000.0, "USD", face_value=1e6)
        result = resolve_positions([pos])
        assert len(result) == 1
        assert result[0] is pos

    def test_future_position_passes_through(self):
        pos = FuturePosition("SPX-SEP26", AssetClass.EQUITY, 250_000.0, "USD")
        result = resolve_positions([pos])
        assert result[0] is pos

    def test_empty_list(self):
        result = resolve_positions([])
        assert result == []


@pytest.mark.unit
class TestResolvePositionsOption:
    def test_option_with_market_data_produces_delta_adjusted_exposure(self):
        opt = OptionPosition(
            instrument_id="AAPL-C-200",
            underlying_id="AAPL",
            option_type=OptionType.CALL,
            strike=200.0,
            expiry_days=30,
            spot_price=195.0,
            implied_vol=0.25,
            risk_free_rate=0.05,
            quantity=10.0,
            contract_multiplier=100.0,
            asset_class=AssetClass.EQUITY,
        )
        result = resolve_positions([opt])
        assert len(result) == 1
        resolved = result[0]
        assert type(resolved) is PositionRisk
        assert resolved.instrument_id == "AAPL-C-200"
        assert resolved.asset_class == AssetClass.EQUITY

        # Verify the exposure is delta * quantity * spot * multiplier
        expected_delta = bs_delta(opt)
        expected_mv = expected_delta * 10.0 * 195.0 * 100.0
        assert resolved.market_value == pytest.approx(expected_mv, rel=1e-10)

    def test_call_delta_adjusted_exposure_is_positive(self):
        opt = OptionPosition(
            instrument_id="CALL",
            underlying_id="X",
            option_type=OptionType.CALL,
            strike=100.0,
            expiry_days=90,
            spot_price=105.0,
            implied_vol=0.20,
            quantity=1.0,
            contract_multiplier=1.0,
        )
        result = resolve_positions([opt])
        assert result[0].market_value > 0

    def test_put_delta_adjusted_exposure_is_negative(self):
        opt = OptionPosition(
            instrument_id="PUT",
            underlying_id="X",
            option_type=OptionType.PUT,
            strike=100.0,
            expiry_days=90,
            spot_price=95.0,
            implied_vol=0.20,
            quantity=1.0,
            contract_multiplier=1.0,
        )
        result = resolve_positions([opt])
        assert result[0].market_value < 0

    def test_option_without_market_data_passes_through(self):
        """Options with spot=0 (not enriched) pass through unchanged."""
        opt = OptionPosition(
            instrument_id="OPT",
            underlying_id="X",
            option_type=OptionType.CALL,
            strike=100.0,
            expiry_days=0,
            spot_price=0.0,
            implied_vol=0.0,
        )
        result = resolve_positions([opt])
        assert result[0] is opt

    def test_contract_multiplier_scales_exposure(self):
        base = OptionPosition(
            instrument_id="OPT",
            underlying_id="X",
            option_type=OptionType.CALL,
            strike=100.0,
            expiry_days=30,
            spot_price=105.0,
            implied_vol=0.20,
            quantity=1.0,
            contract_multiplier=1.0,
        )
        scaled = OptionPosition(
            instrument_id="OPT",
            underlying_id="X",
            option_type=OptionType.CALL,
            strike=100.0,
            expiry_days=30,
            spot_price=105.0,
            implied_vol=0.20,
            quantity=1.0,
            contract_multiplier=100.0,
        )
        result_base = resolve_positions([base])
        result_scaled = resolve_positions([scaled])
        assert result_scaled[0].market_value == pytest.approx(
            result_base[0].market_value * 100.0, rel=1e-10
        )


@pytest.mark.unit
class TestResolvePositionsMixed:
    def test_mixed_portfolio_resolves_only_options(self):
        positions = [
            PositionRisk("AAPL", AssetClass.EQUITY, 500_000.0, "USD"),
            OptionPosition(
                instrument_id="AAPL-C",
                underlying_id="AAPL",
                option_type=OptionType.CALL,
                strike=200.0,
                expiry_days=30,
                spot_price=195.0,
                implied_vol=0.25,
                quantity=5.0,
                contract_multiplier=100.0,
                asset_class=AssetClass.EQUITY,
            ),
            BondPosition("US10Y", AssetClass.FIXED_INCOME, 980_000.0, "USD"),
        ]
        result = resolve_positions(positions)
        assert len(result) == 3
        # First and third pass through
        assert result[0] is positions[0]
        assert result[2] is positions[2]
        # Second is resolved
        assert type(result[1]) is PositionRisk
        assert result[1].instrument_id == "AAPL-C"


def _make_swap(
    instrument_id="USD-SOFR-5Y",
    currency="USD",
    notional=10_000_000.0,
    fixed_rate=0.035,
    maturity_date="2031-04-01",
    pay_receive="PAY_FIXED",
    market_value=0.0,
) -> SwapPosition:
    return SwapPosition(
        instrument_id=instrument_id,
        asset_class=AssetClass.DERIVATIVE,
        market_value=market_value,
        currency=currency,
        instrument_type="INTEREST_RATE_SWAP",
        notional=notional,
        fixed_rate=fixed_rate,
        maturity_date=maturity_date,
        pay_receive=pay_receive,
    )


def _make_yield_curve(rate=0.04) -> YieldCurveData:
    """Flat yield curve at the given rate."""
    return YieldCurveData(tenors=[
        (90, rate),
        (365, rate),
        (730, rate),
        (1825, rate),
        (3650, rate),
    ])


def _make_bundle(currency="USD", rate=0.04) -> MarketDataBundle:
    return MarketDataBundle(yield_curves={currency: _make_yield_curve(rate)})


@pytest.mark.unit
class TestResolvePositionsSwap:
    def test_swap_without_bundle_passes_through(self):
        swap = _make_swap()
        result = resolve_positions([swap])
        assert len(result) == 1
        assert result[0] is swap

    def test_swap_without_matching_currency_passes_through(self):
        swap = _make_swap(currency="EUR")
        bundle = _make_bundle(currency="USD")
        result = resolve_positions([swap], bundle=bundle)
        assert result[0] is swap

    def test_swap_with_yield_curve_produces_dv01_exposure(self):
        swap = _make_swap(notional=10_000_000.0, fixed_rate=0.035, maturity_date="2031-04-01")
        bundle = _make_bundle(currency="USD", rate=0.04)
        result = resolve_positions([swap], bundle=bundle)
        assert len(result) == 1
        resolved = result[0]
        assert type(resolved) is PositionRisk
        assert resolved.instrument_id == "USD-SOFR-5Y"
        assert resolved.currency == "USD"
        assert resolved.market_value > 0  # DV01 is always positive

    def test_swap_dv01_is_always_positive_pay_fixed(self):
        swap = _make_swap(pay_receive="PAY_FIXED")
        bundle = _make_bundle()
        result = resolve_positions([swap], bundle=bundle)
        assert result[0].market_value > 0

    def test_swap_dv01_is_always_positive_receive_fixed(self):
        swap = _make_swap(pay_receive="RECEIVE_FIXED")
        bundle = _make_bundle()
        result = resolve_positions([swap], bundle=bundle)
        assert result[0].market_value > 0

    def test_swap_preserves_instrument_id_and_currency(self):
        swap = _make_swap(instrument_id="EUR-ESTR-10Y", currency="EUR")
        bundle = _make_bundle(currency="EUR")
        result = resolve_positions([swap], bundle=bundle)
        assert result[0].instrument_id == "EUR-ESTR-10Y"
        assert result[0].currency == "EUR"
        assert result[0].asset_class == AssetClass.DERIVATIVE

    def test_mixed_portfolio_resolves_swaps_and_options(self):
        swap = _make_swap()
        opt = OptionPosition(
            instrument_id="AAPL-C",
            underlying_id="AAPL",
            option_type=OptionType.CALL,
            strike=200.0,
            expiry_days=30,
            spot_price=195.0,
            implied_vol=0.25,
            quantity=5.0,
            contract_multiplier=100.0,
        )
        equity = PositionRisk("AAPL", AssetClass.EQUITY, 500_000.0, "USD")
        bundle = _make_bundle()
        result = resolve_positions([swap, opt, equity], bundle=bundle)
        assert len(result) == 3
        # Swap resolved to DV01
        assert type(result[0]) is PositionRisk
        assert result[0].instrument_id == "USD-SOFR-5Y"
        assert result[0].market_value > 0
        # Option resolved to delta-adjusted
        assert type(result[1]) is PositionRisk
        assert result[1].instrument_id == "AAPL-C"
        # Equity passes through
        assert result[2] is equity
