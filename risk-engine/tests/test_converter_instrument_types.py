"""Tests for proto_positions_to_domain with instrument type conversion."""

import pytest

from kinetix.common import types_pb2
from kinetix_risk.converters import proto_positions_to_domain
from kinetix_risk.models import (
    BondPosition,
    FuturePosition,
    FxPosition,
    OptionPosition,
    OptionType,
    PositionRisk,
    SwapPosition,
)


def _make_position(
    instrument_id="INST-1",
    asset_class=types_pb2.EQUITY,
    quantity=100.0,
    market_value="1000000",
    currency="USD",
    instrument_type=types_pb2.INSTRUMENT_TYPE_UNSPECIFIED,
    **kwargs,
):
    """Build a proto Position with optional instrument attributes."""
    pos = types_pb2.Position(
        instrument_id=types_pb2.InstrumentId(value=instrument_id),
        asset_class=asset_class,
        quantity=quantity,
        market_value=types_pb2.Money(amount=market_value, currency=currency),
    )
    pos.instrument_type = instrument_type

    if "option_attrs" in kwargs:
        pos.option_attrs.CopyFrom(kwargs["option_attrs"])
    if "bond_attrs" in kwargs:
        pos.bond_attrs.CopyFrom(kwargs["bond_attrs"])
    if "future_attrs" in kwargs:
        pos.future_attrs.CopyFrom(kwargs["future_attrs"])
    if "fx_attrs" in kwargs:
        pos.fx_attrs.CopyFrom(kwargs["fx_attrs"])
    if "swap_attrs" in kwargs:
        pos.swap_attrs.CopyFrom(kwargs["swap_attrs"])

    return pos


@pytest.mark.unit
class TestConverterFallback:
    def test_unspecified_type_produces_position_risk(self):
        pos = _make_position()
        result = proto_positions_to_domain([pos])
        assert len(result) == 1
        assert type(result[0]) is PositionRisk
        assert result[0].instrument_id == "INST-1"
        assert result[0].market_value == 1_000_000.0

    def test_missing_asset_class_skipped(self):
        pos = _make_position(asset_class=types_pb2.ASSET_CLASS_UNSPECIFIED)
        result = proto_positions_to_domain([pos])
        assert len(result) == 0


@pytest.mark.unit
class TestConverterOptionPosition:
    def test_equity_option_produces_option_position(self):
        attrs = types_pb2.OptionAttributes(
            underlying_id="AAPL",
            option_type="CALL",
            strike=150.0,
            expiry_date="2026-06-20",
            exercise_style="EUROPEAN",
            contract_multiplier=100.0,
            dividend_yield=0.005,
        )
        pos = _make_position(
            instrument_type=types_pb2.EQUITY_OPTION,
            option_attrs=attrs,
        )
        result = proto_positions_to_domain([pos])
        assert len(result) == 1
        opt = result[0]
        assert isinstance(opt, OptionPosition)
        assert opt.underlying_id == "AAPL"
        assert opt.option_type == OptionType.CALL
        assert opt.strike == 150.0
        assert opt.dividend_yield == 0.005
        assert opt.contract_multiplier == 100.0
        assert opt.quantity == 100.0

    def test_fx_option_produces_option_position(self):
        attrs = types_pb2.OptionAttributes(
            underlying_id="EURUSD",
            option_type="PUT",
            strike=1.08,
            contract_multiplier=1.0,
        )
        pos = _make_position(
            instrument_id="EURUSD-P",
            asset_class=types_pb2.FX,
            instrument_type=types_pb2.FX_OPTION,
            option_attrs=attrs,
        )
        result = proto_positions_to_domain([pos])
        assert len(result) == 1
        assert isinstance(result[0], OptionPosition)
        assert result[0].option_type == OptionType.PUT


@pytest.mark.unit
class TestConverterBondPosition:
    def test_government_bond_produces_bond_position(self):
        attrs = types_pb2.BondAttributes(
            face_value=1_000_000.0,
            coupon_rate=0.025,
            coupon_frequency=2,
            maturity_date="2036-05-15",
            day_count_convention="ACT/ACT",
        )
        pos = _make_position(
            instrument_id="US10Y",
            asset_class=types_pb2.FIXED_INCOME,
            instrument_type=types_pb2.GOVERNMENT_BOND,
            bond_attrs=attrs,
        )
        result = proto_positions_to_domain([pos])
        assert len(result) == 1
        bond = result[0]
        assert isinstance(bond, BondPosition)
        assert bond.face_value == 1_000_000.0
        assert bond.coupon_rate == 0.025
        assert bond.maturity_date == "2036-05-15"

    def test_corporate_bond_produces_bond_position(self):
        attrs = types_pb2.BondAttributes(
            face_value=500_000.0,
            coupon_rate=0.045,
            issuer="JPM",
            credit_rating="A+",
            seniority="SENIOR_UNSECURED",
        )
        pos = _make_position(
            instrument_id="JPM-BOND",
            asset_class=types_pb2.FIXED_INCOME,
            instrument_type=types_pb2.CORPORATE_BOND,
            bond_attrs=attrs,
        )
        result = proto_positions_to_domain([pos])
        assert isinstance(result[0], BondPosition)
        assert result[0].issuer == "JPM"
        assert result[0].credit_rating == "A+"


@pytest.mark.unit
class TestConverterFuturePosition:
    def test_equity_future_produces_future_position(self):
        attrs = types_pb2.FutureAttributes(
            underlying_id="SPX",
            expiry_date="2026-09-18",
            contract_size=50.0,
        )
        pos = _make_position(
            instrument_id="SPX-SEP26",
            instrument_type=types_pb2.EQUITY_FUTURE,
            future_attrs=attrs,
        )
        result = proto_positions_to_domain([pos])
        assert isinstance(result[0], FuturePosition)
        assert result[0].underlying_id == "SPX"
        assert result[0].contract_size == 50.0

    def test_commodity_future_produces_future_position(self):
        attrs = types_pb2.FutureAttributes(
            underlying_id="WTI",
            expiry_date="2026-08-20",
            contract_size=1000.0,
        )
        pos = _make_position(
            instrument_id="WTI-AUG26",
            asset_class=types_pb2.COMMODITY,
            instrument_type=types_pb2.COMMODITY_FUTURE,
            future_attrs=attrs,
        )
        result = proto_positions_to_domain([pos])
        assert isinstance(result[0], FuturePosition)


@pytest.mark.unit
class TestConverterFxPosition:
    def test_fx_spot_produces_fx_position(self):
        attrs = types_pb2.FxAttributes(
            base_currency="EUR",
            quote_currency="USD",
        )
        pos = _make_position(
            instrument_id="EURUSD",
            asset_class=types_pb2.FX,
            instrument_type=types_pb2.FX_SPOT,
            fx_attrs=attrs,
        )
        result = proto_positions_to_domain([pos])
        assert isinstance(result[0], FxPosition)
        assert result[0].base_currency == "EUR"
        assert result[0].quote_currency == "USD"

    def test_fx_forward_produces_fx_position(self):
        attrs = types_pb2.FxAttributes(
            base_currency="GBP",
            quote_currency="USD",
            delivery_date="2026-06-16",
            forward_rate=1.28,
        )
        pos = _make_position(
            instrument_id="GBPUSD-3M",
            asset_class=types_pb2.FX,
            instrument_type=types_pb2.FX_FORWARD,
            fx_attrs=attrs,
        )
        result = proto_positions_to_domain([pos])
        assert isinstance(result[0], FxPosition)
        assert result[0].forward_rate == 1.28


@pytest.mark.unit
class TestConverterExpiryComputation:
    def test_option_computes_expiry_days_from_expiry_date(self):
        from datetime import date, timedelta

        future_date = (date.today() + timedelta(days=30)).isoformat()
        attrs = types_pb2.OptionAttributes(
            underlying_id="AAPL",
            option_type="CALL",
            strike=150.0,
            expiry_date=future_date,
            contract_multiplier=100.0,
        )
        pos = _make_position(instrument_type=types_pb2.EQUITY_OPTION, option_attrs=attrs)
        result = proto_positions_to_domain([pos])
        assert isinstance(result[0], OptionPosition)
        assert result[0].expiry_days == 30

    def test_expired_option_gets_zero_expiry_days(self):
        from datetime import date, timedelta

        past_date = (date.today() - timedelta(days=5)).isoformat()
        attrs = types_pb2.OptionAttributes(
            underlying_id="AAPL",
            option_type="CALL",
            strike=150.0,
            expiry_date=past_date,
            contract_multiplier=100.0,
        )
        pos = _make_position(instrument_type=types_pb2.EQUITY_OPTION, option_attrs=attrs)
        result = proto_positions_to_domain([pos])
        assert isinstance(result[0], OptionPosition)
        assert result[0].expiry_days == 0


@pytest.mark.unit
class TestConverterSwapPosition:
    def test_interest_rate_swap_produces_swap_position(self):
        attrs = types_pb2.SwapAttributes(
            notional=10_000_000.0,
            fixed_rate=0.035,
            float_index="SOFR",
            float_spread=0.001,
            effective_date="2026-03-16",
            maturity_date="2031-03-16",
            pay_receive="PAY_FIXED",
            fixed_frequency=2,
            float_frequency=4,
            day_count_convention="ACT/360",
        )
        pos = _make_position(
            instrument_id="USD-SOFR-5Y",
            asset_class=types_pb2.FIXED_INCOME,
            instrument_type=types_pb2.INTEREST_RATE_SWAP,
            swap_attrs=attrs,
        )
        result = proto_positions_to_domain([pos])
        assert isinstance(result[0], SwapPosition)
        assert result[0].notional == 10_000_000.0
        assert result[0].fixed_rate == 0.035
        assert result[0].float_index == "SOFR"
        assert result[0].pay_receive == "PAY_FIXED"
