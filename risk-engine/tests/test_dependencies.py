import pytest

from kinetix_risk.dependencies import (
    DEPENDENCIES_REGISTRY,
    MarketDataDependency,
    discover,
)
from kinetix_risk.models import AssetClass, PositionRisk


def _pos(instrument_id: str, asset_class: AssetClass, market_value: float = 100_000.0) -> PositionRisk:
    return PositionRisk(
        instrument_id=instrument_id,
        asset_class=asset_class,
        market_value=market_value,
        currency="USD",
    )


class TestDependenciesRegistry:
    def test_every_asset_class_has_at_least_one_dependency(self):
        for ac in AssetClass:
            assert ac in DEPENDENCIES_REGISTRY, f"{ac} missing from registry"
            assert len(DEPENDENCIES_REGISTRY[ac]) >= 1, f"{ac} has no dependencies"

    def test_equity_dependencies(self):
        templates = DEPENDENCIES_REGISTRY[AssetClass.EQUITY]
        data_types = [t.data_type for t in templates]
        assert "SPOT_PRICE" in data_types
        assert "HISTORICAL_PRICES" in data_types

    def test_fixed_income_dependencies(self):
        templates = DEPENDENCIES_REGISTRY[AssetClass.FIXED_INCOME]
        data_types = [t.data_type for t in templates]
        assert "YIELD_CURVE" in data_types
        assert "CREDIT_SPREAD" in data_types

    def test_fx_dependencies(self):
        templates = DEPENDENCIES_REGISTRY[AssetClass.FX]
        data_types = [t.data_type for t in templates]
        assert "SPOT_PRICE" in data_types
        assert "FORWARD_CURVE" in data_types

    def test_commodity_dependencies(self):
        templates = DEPENDENCIES_REGISTRY[AssetClass.COMMODITY]
        data_types = [t.data_type for t in templates]
        assert "SPOT_PRICE" in data_types
        assert "FORWARD_CURVE" in data_types

    def test_derivative_dependencies(self):
        templates = DEPENDENCIES_REGISTRY[AssetClass.DERIVATIVE]
        data_types = [t.data_type for t in templates]
        assert "SPOT_PRICE" in data_types
        assert "VOLATILITY_SURFACE" in data_types
        assert "RISK_FREE_RATE" in data_types
        assert "DIVIDEND_YIELD" in data_types

    def test_derivative_has_required_flags(self):
        templates = DEPENDENCIES_REGISTRY[AssetClass.DERIVATIVE]
        by_type = {t.data_type: t for t in templates}
        assert by_type["SPOT_PRICE"].required is True
        assert by_type["VOLATILITY_SURFACE"].required is True
        assert by_type["RISK_FREE_RATE"].required is True
        assert by_type["DIVIDEND_YIELD"].required is False


class TestDiscover:
    def test_empty_positions_returns_empty(self):
        result = discover([])
        assert result == []

    def test_single_equity_position(self):
        positions = [_pos("AAPL", AssetClass.EQUITY)]
        result = discover(positions)

        data_types = [(d.data_type, d.instrument_id) for d in result]
        assert ("SPOT_PRICE", "AAPL") in data_types
        assert ("HISTORICAL_PRICES", "AAPL") in data_types

    def test_single_derivative_position(self):
        positions = [_pos("AAPL-C-250-20260620", AssetClass.DERIVATIVE)]
        result = discover(positions)

        data_types = [(d.data_type, d.instrument_id) for d in result]
        assert ("SPOT_PRICE", "AAPL-C-250-20260620") in data_types
        assert ("VOLATILITY_SURFACE", "AAPL-C-250-20260620") in data_types
        assert ("RISK_FREE_RATE", "") in data_types
        assert ("DIVIDEND_YIELD", "AAPL-C-250-20260620") in data_types

    def test_deduplication_same_instrument(self):
        positions = [
            _pos("AAPL", AssetClass.EQUITY, 100_000.0),
            _pos("AAPL", AssetClass.EQUITY, 200_000.0),
        ]
        result = discover(positions)

        spot_prices = [d for d in result if d.data_type == "SPOT_PRICE" and d.instrument_id == "AAPL"]
        assert len(spot_prices) == 1

    def test_deduplication_different_instruments(self):
        positions = [
            _pos("AAPL", AssetClass.EQUITY),
            _pos("GOOGL", AssetClass.EQUITY),
        ]
        result = discover(positions)

        spot_prices = [d for d in result if d.data_type == "SPOT_PRICE"]
        assert len(spot_prices) == 2
        instruments = {d.instrument_id for d in spot_prices}
        assert instruments == {"AAPL", "GOOGL"}

    def test_mixed_portfolio_equity_and_derivative(self):
        positions = [
            _pos("AAPL", AssetClass.EQUITY),
            _pos("AAPL-C-250-20260620", AssetClass.DERIVATIVE),
        ]
        result = discover(positions)

        data_type_instrument_pairs = {(d.data_type, d.instrument_id) for d in result}
        # Equity deps
        assert ("SPOT_PRICE", "AAPL") in data_type_instrument_pairs
        assert ("HISTORICAL_PRICES", "AAPL") in data_type_instrument_pairs
        # Derivative deps
        assert ("SPOT_PRICE", "AAPL-C-250-20260620") in data_type_instrument_pairs
        assert ("VOLATILITY_SURFACE", "AAPL-C-250-20260620") in data_type_instrument_pairs
        assert ("RISK_FREE_RATE", "") in data_type_instrument_pairs
        # Multi-asset → correlation matrix
        assert ("CORRELATION_MATRIX", "") in data_type_instrument_pairs

    def test_correlation_matrix_only_for_multiple_asset_classes(self):
        # Single asset class → no correlation matrix
        positions = [
            _pos("AAPL", AssetClass.EQUITY),
            _pos("GOOGL", AssetClass.EQUITY),
        ]
        result = discover(positions)
        data_types = [d.data_type for d in result]
        assert "CORRELATION_MATRIX" not in data_types

    def test_correlation_matrix_with_multiple_asset_classes(self):
        positions = [
            _pos("AAPL", AssetClass.EQUITY),
            _pos("GOLD", AssetClass.COMMODITY),
        ]
        result = discover(positions)
        corr = [d for d in result if d.data_type == "CORRELATION_MATRIX"]
        assert len(corr) == 1
        assert corr[0].instrument_id == ""
        assert corr[0].required is True

    def test_portfolio_level_dependencies_not_duplicated(self):
        positions = [
            _pos("OPT-1", AssetClass.DERIVATIVE),
            _pos("OPT-2", AssetClass.DERIVATIVE),
        ]
        result = discover(positions)

        risk_free_rates = [d for d in result if d.data_type == "RISK_FREE_RATE"]
        assert len(risk_free_rates) == 1
        assert risk_free_rates[0].instrument_id == ""

    def test_asset_class_set_correctly(self):
        positions = [_pos("AAPL", AssetClass.EQUITY)]
        result = discover(positions)
        for dep in result:
            assert dep.asset_class == "EQUITY"

    def test_parameters_preserved(self):
        positions = [_pos("AAPL", AssetClass.EQUITY)]
        result = discover(positions)
        hist = [d for d in result if d.data_type == "HISTORICAL_PRICES"]
        assert len(hist) == 1
        assert hist[0].parameters == {"lookbackDays": "252"}

    def test_derivative_risk_free_rate_has_currency_parameter(self):
        positions = [_pos("OPT-1", AssetClass.DERIVATIVE)]
        result = discover(positions)
        rfr = [d for d in result if d.data_type == "RISK_FREE_RATE"]
        assert len(rfr) == 1
        assert rfr[0].parameters == {"currency": "USD"}

    def test_all_asset_classes_in_single_portfolio(self):
        positions = [
            _pos("AAPL", AssetClass.EQUITY),
            _pos("TBOND-10Y", AssetClass.FIXED_INCOME),
            _pos("EURUSD", AssetClass.FX),
            _pos("GOLD", AssetClass.COMMODITY),
            _pos("AAPL-C-250", AssetClass.DERIVATIVE),
        ]
        result = discover(positions)
        data_types = {d.data_type for d in result}

        assert "SPOT_PRICE" in data_types
        assert "HISTORICAL_PRICES" in data_types
        assert "YIELD_CURVE" in data_types
        assert "CREDIT_SPREAD" in data_types
        assert "FORWARD_CURVE" in data_types
        assert "VOLATILITY_SURFACE" in data_types
        assert "RISK_FREE_RATE" in data_types
        assert "DIVIDEND_YIELD" in data_types
        assert "CORRELATION_MATRIX" in data_types

    def test_description_is_nonempty(self):
        positions = [_pos("AAPL", AssetClass.EQUITY)]
        result = discover(positions)
        for dep in result:
            assert dep.description, f"Empty description for {dep.data_type}"

    def test_fixed_income_yield_curve_is_portfolio_level(self):
        positions = [
            _pos("BOND-1", AssetClass.FIXED_INCOME),
            _pos("BOND-2", AssetClass.FIXED_INCOME),
        ]
        result = discover(positions)
        yc = [d for d in result if d.data_type == "YIELD_CURVE"]
        assert len(yc) == 1
        assert yc[0].instrument_id == ""

    def test_fixed_income_credit_spread_is_per_instrument(self):
        positions = [
            _pos("BOND-1", AssetClass.FIXED_INCOME),
            _pos("BOND-2", AssetClass.FIXED_INCOME),
        ]
        result = discover(positions)
        cs = [d for d in result if d.data_type == "CREDIT_SPREAD"]
        assert len(cs) == 2
        instruments = {d.instrument_id for d in cs}
        assert instruments == {"BOND-1", "BOND-2"}
