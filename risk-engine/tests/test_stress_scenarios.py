import numpy as np
import pytest

from kinetix_risk.models import AssetClass, StressScenario
from kinetix_risk.stress.scenarios import (
    HISTORICAL_SCENARIOS, get_scenario, list_scenarios,
)


class TestHistoricalScenarios:
    def test_gfc_2008_exists_with_expected_shocks(self):
        scenario = get_scenario("GFC_2008")
        assert scenario.name == "GFC_2008"
        assert scenario.price_shocks[AssetClass.EQUITY] == 0.60  # 40% drop
        assert scenario.price_shocks[AssetClass.COMMODITY] == 0.70  # 30% drop
        assert scenario.vol_shocks[AssetClass.EQUITY] == 3.0  # vol x3
        assert scenario.correlation_override is not None

    def test_covid_2020_has_equity_vol_shock(self):
        scenario = get_scenario("COVID_2020")
        assert scenario.vol_shocks[AssetClass.EQUITY] == 2.5
        assert scenario.price_shocks[AssetClass.EQUITY] == 0.65  # 35% drop
        assert scenario.price_shocks[AssetClass.COMMODITY] == 0.75  # 25% drop
        assert scenario.correlation_override is not None

    def test_all_scenarios_have_valid_structure(self):
        for name, scenario in HISTORICAL_SCENARIOS.items():
            assert isinstance(scenario, StressScenario)
            assert scenario.name == name
            assert len(scenario.description) > 0
            assert len(scenario.vol_shocks) > 0
            assert len(scenario.price_shocks) > 0
            for shock in scenario.vol_shocks.values():
                assert shock >= 1.0, f"Vol shock in {name} should be >= 1.0 (multiplicative)"
            for shock in scenario.price_shocks.values():
                assert 0.0 < shock <= 1.0, f"Price shock in {name} should be in (0, 1]"
            if scenario.correlation_override is not None:
                assert scenario.correlation_override.shape == (5, 5)
                np.testing.assert_array_almost_equal(
                    np.diag(scenario.correlation_override), np.ones(5),
                )

    def test_get_unknown_scenario_raises(self):
        with pytest.raises(KeyError, match="Unknown stress scenario"):
            get_scenario("NONEXISTENT_CRISIS")

    def test_list_scenarios_returns_all_names(self):
        names = list_scenarios()
        assert "GFC_2008" in names
        assert "COVID_2020" in names
        assert "TAPER_TANTRUM_2013" in names
        assert "EURO_CRISIS_2011" in names
        assert len(names) == 4
