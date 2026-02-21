import numpy as np

from kinetix_risk.models import AssetClass, StressScenario


def build_hypothetical_scenario(
    name: str,
    description: str,
    vol_shocks: dict[AssetClass, float] | None = None,
    correlation_override: np.ndarray | None = None,
    price_shocks: dict[AssetClass, float] | None = None,
) -> StressScenario:
    if not name:
        raise ValueError("Scenario name must not be empty")
    return StressScenario(
        name=name,
        description=description or "",
        vol_shocks=vol_shocks or {},
        correlation_override=correlation_override,
        price_shocks=price_shocks or {},
    )
