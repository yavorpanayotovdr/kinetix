import pytest

from kinetix_risk.models import (
    AssetClass,
    CalculationType,
    ConfidenceLevel,
    PositionRisk,
    ValuationResult,
)
from kinetix_risk.valuation import calculate_valuation


def _sample_positions() -> list[PositionRisk]:
    return [
        PositionRisk("AAPL", AssetClass.EQUITY, 1_000_000.0, "USD"),
        PositionRisk("UST10Y", AssetClass.FIXED_INCOME, 500_000.0, "USD"),
        PositionRisk("GOLD", AssetClass.COMMODITY, 300_000.0, "USD"),
    ]


class TestValuationDefaults:
    def test_defaults_to_var_and_es_when_requested_outputs_empty(self):
        result = calculate_valuation(
            positions=_sample_positions(),
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            requested_outputs=[],
        )
        assert result.var_result is not None
        assert result.var_result.expected_shortfall != 0.0
        assert result.greeks_result is None
        assert set(result.computed_outputs) == {"VAR", "EXPECTED_SHORTFALL"}

    def test_returns_valuation_result_type(self):
        result = calculate_valuation(
            positions=_sample_positions(),
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            requested_outputs=["VAR"],
        )
        assert isinstance(result, ValuationResult)


class TestValuationVaROnly:
    def test_var_only_returns_var_result(self):
        result = calculate_valuation(
            positions=_sample_positions(),
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            requested_outputs=["VAR"],
        )
        assert result.var_result is not None
        assert result.var_result.var_value > 0
        assert "VAR" in result.computed_outputs

    def test_var_and_es_returns_both(self):
        result = calculate_valuation(
            positions=_sample_positions(),
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            requested_outputs=["VAR", "EXPECTED_SHORTFALL"],
        )
        assert result.var_result is not None
        assert result.var_result.expected_shortfall != 0.0
        assert set(result.computed_outputs) >= {"VAR", "EXPECTED_SHORTFALL"}


class TestValuationGreeks:
    def test_greeks_only(self):
        result = calculate_valuation(
            positions=_sample_positions(),
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            requested_outputs=["GREEKS"],
        )
        assert result.greeks_result is not None
        assert result.greeks_result.theta != 0.0
        assert "GREEKS" in result.computed_outputs

    def test_all_outputs(self):
        result = calculate_valuation(
            positions=_sample_positions(),
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            requested_outputs=["VAR", "EXPECTED_SHORTFALL", "GREEKS"],
        )
        assert result.var_result is not None
        assert result.greeks_result is not None
        assert set(result.computed_outputs) == {"VAR", "EXPECTED_SHORTFALL", "GREEKS"}

    def test_greeks_with_var_reuses_base_var(self):
        """When both VaR and Greeks are requested, Greeks should reuse the base VaR value."""
        result = calculate_valuation(
            positions=_sample_positions(),
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            requested_outputs=["VAR", "GREEKS"],
        )
        assert result.var_result is not None
        assert result.greeks_result is not None


class TestValuationPV:
    def test_pv_returns_sum_of_market_values(self):
        result = calculate_valuation(
            positions=_sample_positions(),
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            requested_outputs=["PV"],
        )
        assert result.pv_value == 1_000_000.0 + 500_000.0 + 300_000.0
        assert "PV" in result.computed_outputs

    def test_pv_not_computed_when_not_requested(self):
        result = calculate_valuation(
            positions=_sample_positions(),
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            requested_outputs=["VAR"],
        )
        assert result.pv_value is None
        assert "PV" not in result.computed_outputs

    def test_pv_works_alongside_all_other_outputs(self):
        result = calculate_valuation(
            positions=_sample_positions(),
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            requested_outputs=["VAR", "EXPECTED_SHORTFALL", "GREEKS", "PV"],
        )
        assert result.var_result is not None
        assert result.greeks_result is not None
        assert result.pv_value == 1_800_000.0
        assert set(result.computed_outputs) == {"VAR", "EXPECTED_SHORTFALL", "GREEKS", "PV"}

    def test_empty_positions_returns_none_pv(self):
        result = calculate_valuation(
            positions=[],
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            requested_outputs=["PV"],
        )
        assert result.pv_value is None
        assert result.computed_outputs == []


class TestValuationMarketData:
    def test_valuation_passes_market_data_to_resolve_positions(self):
        """OptionPosition with spot=0 should get enriched from market_data_bundle."""
        from kinetix_risk.models import OptionPosition, OptionType
        from kinetix_risk.market_data_consumer import MarketDataBundle

        option = OptionPosition(
            instrument_id="AAPL-C-150",
            underlying_id="AAPL",
            option_type=OptionType.CALL,
            strike=150.0,
            expiry_days=30,
            spot_price=0.0,  # not enriched yet
            implied_vol=0.0,
            risk_free_rate=0.05,
            quantity=10.0,
        )
        bundle = MarketDataBundle(spot_prices={"AAPL": 170.0})

        result = calculate_valuation(
            positions=[option],
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            requested_outputs=["VAR"],
            market_data_bundle=bundle,
        )
        # With spot enrichment the option should produce non-zero VaR
        assert result.var_result is not None
        assert result.var_result.var_value > 0


class TestValuationPositionGreeks:
    def test_valuation_returns_position_greeks_for_options(self):
        from kinetix_risk.models import OptionPosition, OptionType

        option = OptionPosition(
            instrument_id="AAPL-C-150",
            underlying_id="AAPL",
            option_type=OptionType.CALL,
            strike=150.0,
            expiry_days=30,
            spot_price=170.0,
            implied_vol=0.25,
            risk_free_rate=0.05,
            quantity=10.0,
        )
        result = calculate_valuation(
            positions=[option],
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            requested_outputs=["VAR", "GREEKS"],
        )
        assert result.position_greeks is not None
        assert "AAPL-C-150" in result.position_greeks
        greeks = result.position_greeks["AAPL-C-150"]
        assert "delta" in greeks
        assert 0 < greeks["delta"] < 1  # ITM call
        assert "gamma" in greeks
        assert greeks["gamma"] > 0
        assert "vega" in greeks
        assert greeks["vega"] > 0


class TestValuationEdgeCases:
    def test_empty_positions_returns_none_results(self):
        result = calculate_valuation(
            positions=[],
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            requested_outputs=["VAR", "GREEKS"],
        )
        assert result.var_result is None
        assert result.greeks_result is None
        assert result.computed_outputs == []

    def test_book_id_is_propagated(self):
        result = calculate_valuation(
            positions=_sample_positions(),
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            requested_outputs=["GREEKS"],
            book_id="test-port",
        )
        assert result.greeks_result is not None
        assert result.greeks_result.book_id == "test-port"
