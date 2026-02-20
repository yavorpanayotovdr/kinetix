import numpy as np
import pytest

from kinetix_risk.models import AssetClass, AssetClassExposure, ConfidenceLevel
from kinetix_risk.var_historical import calculate_historical_var


class TestHistoricalVaRSingleAsset:
    def test_var_is_positive(self):
        exposures = [AssetClassExposure(AssetClass.EQUITY, 100_000.0, 0.20)]
        corr = np.array([[1.0]])
        result = calculate_historical_var(
            exposures, ConfidenceLevel.CL_95, 1, corr, seed=42,
        )
        assert result.var_value > 0

    def test_time_horizon_scales_with_sqrt_t(self):
        exposures = [AssetClassExposure(AssetClass.EQUITY, 100_000.0, 0.20)]
        corr = np.array([[1.0]])
        var_1d = calculate_historical_var(
            exposures, ConfidenceLevel.CL_95, 1, corr, seed=42,
        )
        var_10d = calculate_historical_var(
            exposures, ConfidenceLevel.CL_95, 10, corr, seed=42,
        )
        assert var_10d.var_value == pytest.approx(
            var_1d.var_value * np.sqrt(10), rel=1e-6
        )

    def test_es_greater_than_var(self):
        exposures = [AssetClassExposure(AssetClass.EQUITY, 100_000.0, 0.20)]
        corr = np.array([[1.0]])
        result = calculate_historical_var(
            exposures, ConfidenceLevel.CL_95, 1, corr, seed=42,
        )
        assert result.expected_shortfall > result.var_value

    def test_99_confidence_higher_than_95(self):
        exposures = [AssetClassExposure(AssetClass.EQUITY, 100_000.0, 0.20)]
        corr = np.array([[1.0]])
        var_95 = calculate_historical_var(
            exposures, ConfidenceLevel.CL_95, 1, corr, seed=42,
        )
        var_99 = calculate_historical_var(
            exposures, ConfidenceLevel.CL_99, 1, corr, seed=42,
        )
        assert var_99.var_value > var_95.var_value


class TestHistoricalVaRMultiAsset:
    def test_diversification_reduces_var(self):
        exposures = [
            AssetClassExposure(AssetClass.EQUITY, 100_000.0, 0.20),
            AssetClassExposure(AssetClass.FIXED_INCOME, 100_000.0, 0.06),
        ]
        corr_neg = np.array([[1.0, -0.20], [-0.20, 1.0]])

        var_combined = calculate_historical_var(
            exposures, ConfidenceLevel.CL_95, 1, corr_neg, seed=42,
        )
        var_eq_only = calculate_historical_var(
            [exposures[0]], ConfidenceLevel.CL_95, 1, np.array([[1.0]]), seed=42,
        )
        var_fi_only = calculate_historical_var(
            [exposures[1]], ConfidenceLevel.CL_95, 1, np.array([[1.0]]), seed=42,
        )
        assert var_combined.var_value < var_eq_only.var_value + var_fi_only.var_value

    def test_component_breakdown_present(self):
        exposures = [
            AssetClassExposure(AssetClass.EQUITY, 100_000.0, 0.20),
            AssetClassExposure(AssetClass.FX, 50_000.0, 0.10),
        ]
        corr = np.array([[1.0, 0.3], [0.3, 1.0]])
        result = calculate_historical_var(
            exposures, ConfidenceLevel.CL_95, 1, corr, seed=42,
        )
        assert len(result.component_breakdown) == 2
        total = sum(c.var_contribution for c in result.component_breakdown)
        assert total == pytest.approx(result.var_value, rel=0.05)


class TestHistoricalVaRConvergesToParametric:
    def test_with_many_scenarios_approaches_parametric(self):
        from kinetix_risk.var_parametric import calculate_parametric_var

        exposures = [AssetClassExposure(AssetClass.EQUITY, 100_000.0, 0.20)]
        corr = np.array([[1.0]])

        parametric = calculate_parametric_var(exposures, ConfidenceLevel.CL_95, 1, corr)
        historical = calculate_historical_var(
            exposures, ConfidenceLevel.CL_95, 1, corr,
            num_scenarios=100_000, seed=42,
        )
        assert historical.var_value == pytest.approx(
            parametric.var_value, rel=0.05
        )
