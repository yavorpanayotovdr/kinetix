from kinetix_risk.models import (
    AssetClass,
    CalculationType,
    ConfidenceLevel,
    PositionRisk,
)
from kinetix_risk.portfolio_risk import calculate_portfolio_var
from kinetix_risk.volatility import DEFAULT_VOLATILITIES, VolatilityProvider


class TestVolatilityProvider:
    def test_static_provider_returns_default_vols(self):
        provider = VolatilityProvider.static()
        for ac, expected_vol in DEFAULT_VOLATILITIES.items():
            assert provider(ac) == expected_vol

    def test_custom_provider_overrides_vols(self):
        custom = {AssetClass.EQUITY: 0.50}
        provider = VolatilityProvider.from_dict(custom)
        assert provider(AssetClass.EQUITY) == 0.50
        # Fallback to default for non-overridden
        assert provider(AssetClass.FX) == DEFAULT_VOLATILITIES[AssetClass.FX]

    def test_portfolio_var_uses_custom_provider(self):
        positions = [
            PositionRisk("AAPL", AssetClass.EQUITY, 1_000_000.0, "USD"),
        ]
        default_result = calculate_portfolio_var(
            positions, CalculationType.PARAMETRIC, ConfidenceLevel.CL_95, 1,
        )
        high_vol_provider = VolatilityProvider.from_dict({AssetClass.EQUITY: 0.50})
        high_vol_result = calculate_portfolio_var(
            positions, CalculationType.PARAMETRIC, ConfidenceLevel.CL_95, 1,
            volatility_provider=high_vol_provider,
        )
        assert high_vol_result.var_value > default_result.var_value

    def test_portfolio_var_default_provider_unchanged(self):
        positions = [
            PositionRisk("AAPL", AssetClass.EQUITY, 1_000_000.0, "USD"),
        ]
        result_no_provider = calculate_portfolio_var(
            positions, CalculationType.PARAMETRIC, ConfidenceLevel.CL_95, 1,
        )
        result_static = calculate_portfolio_var(
            positions, CalculationType.PARAMETRIC, ConfidenceLevel.CL_95, 1,
            volatility_provider=VolatilityProvider.static(),
        )
        assert abs(result_no_provider.var_value - result_static.var_value) < 1e-10
