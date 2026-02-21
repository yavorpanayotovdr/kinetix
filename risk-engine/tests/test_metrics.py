from prometheus_client import REGISTRY

from kinetix_risk.metrics import (
    risk_var_calculation_duration_seconds,
    risk_var_calculation_total,
    risk_var_value,
)
from kinetix_risk.models import (
    AssetClass,
    CalculationType,
    ConfidenceLevel,
    PositionRisk,
)
from kinetix_risk.portfolio_risk import calculate_portfolio_var


def make_equity_position(instrument_id: str, market_value: float) -> PositionRisk:
    return PositionRisk(instrument_id, AssetClass.EQUITY, market_value, "USD")


class TestMetrics:
    def test_counter_increments_after_calculation(self):
        positions = [make_equity_position("AAPL", 100_000.0)]
        before = risk_var_calculation_total.labels(
            calculation_type="PARAMETRIC",
            confidence_level="0.95",
        )._value.get()

        calculate_portfolio_var(
            positions, CalculationType.PARAMETRIC, ConfidenceLevel.CL_95, 1,
        )

        after = risk_var_calculation_total.labels(
            calculation_type="PARAMETRIC",
            confidence_level="0.95",
        )._value.get()
        assert after > before

    def test_histogram_has_observations(self):
        positions = [make_equity_position("AAPL", 100_000.0)]
        calculate_portfolio_var(
            positions, CalculationType.PARAMETRIC, ConfidenceLevel.CL_95, 1,
        )

        # Collect all samples from the histogram and check _count
        sample_value = None
        for metric in REGISTRY.collect():
            if metric.name == "risk_var_calculation_duration_seconds":
                for sample in metric.samples:
                    if sample.name == "risk_var_calculation_duration_seconds_count":
                        sample_value = sample.value
                        break
        assert sample_value is not None and sample_value > 0

    def test_gauge_is_set_to_var_value(self):
        positions = [make_equity_position("AAPL", 100_000.0)]
        result = calculate_portfolio_var(
            positions, CalculationType.PARAMETRIC, ConfidenceLevel.CL_95, 1,
        )

        risk_var_value.labels(portfolio_id="test-port").set(result.var_value)
        assert risk_var_value.labels(portfolio_id="test-port")._value.get() == result.var_value
