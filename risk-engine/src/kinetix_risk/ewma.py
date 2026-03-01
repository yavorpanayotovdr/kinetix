import numpy as np

from kinetix_risk.models import AssetClass
from kinetix_risk.volatility import DEFAULT_VOLATILITIES, VolatilityProvider


def ewma_volatility(
    returns: np.ndarray,
    decay_factor: float = 0.94,
    initial_vol: float | None = None,
) -> float:
    """Compute EWMA volatility from a series of returns.

    vol_t^2 = lambda * vol_{t-1}^2 + (1 - lambda) * r_t^2

    Returns the daily volatility (standard deviation) estimate.
    """
    if len(returns) == 0:
        if initial_vol is not None:
            return initial_vol
        raise ValueError("Cannot compute EWMA volatility with no returns and no initial_vol")

    if initial_vol is not None:
        variance = initial_vol ** 2
    else:
        variance = returns[0] ** 2

    for r in returns if initial_vol is not None else returns[1:]:
        variance = decay_factor * variance + (1 - decay_factor) * r ** 2

    return float(np.sqrt(variance))


class EwmaVolatilityProvider:
    """Produces a VolatilityProvider that estimates vol from historical
    returns using EWMA. Falls back to static defaults for asset classes
    without return history."""

    def __init__(
        self,
        returns_by_asset_class: dict[AssetClass, np.ndarray],
        decay_factor: float = 0.94,
    ):
        self._returns_by_asset_class = returns_by_asset_class
        self._decay_factor = decay_factor

    def get_provider(self) -> VolatilityProvider:
        vols: dict[AssetClass, float] = {}
        for ac, returns in self._returns_by_asset_class.items():
            vols[ac] = ewma_volatility(returns, self._decay_factor)

        def lookup(ac: AssetClass) -> float:
            return vols.get(ac, DEFAULT_VOLATILITIES[ac])

        return VolatilityProvider(lookup)
