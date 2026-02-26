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

    def test_portfolio_id_is_propagated(self):
        result = calculate_valuation(
            positions=_sample_positions(),
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            requested_outputs=["GREEKS"],
            portfolio_id="test-port",
        )
        assert result.greeks_result is not None
        assert result.greeks_result.portfolio_id == "test-port"
