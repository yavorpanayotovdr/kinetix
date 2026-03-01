"""Dynamic correlation estimation using Ledoit-Wolf shrinkage.

Converts the Ledoit-Wolf covariance estimate into a correlation matrix
by normalising by the standard deviations. This produces a well-conditioned,
positive-definite correlation matrix even when the number of observations
is small relative to the number of assets.
"""

import numpy as np
from sklearn.covariance import LedoitWolf


def estimate_correlation(
    returns: np.ndarray,
    method: str = "ledoit_wolf",
) -> np.ndarray:
    """Estimate a correlation matrix from a returns matrix.

    Args:
        returns: (n_observations, n_assets) array of asset returns.
        method: Estimation method. Currently only "ledoit_wolf" is supported.

    Returns:
        (n_assets, n_assets) correlation matrix with unit diagonal.

    Raises:
        ValueError: If an unknown method is specified.
    """
    if method != "ledoit_wolf":
        raise ValueError(f"Unknown correlation estimation method: '{method}'")

    lw = LedoitWolf()
    lw.fit(returns)
    covariance = lw.covariance_

    # Convert covariance to correlation
    d = np.sqrt(np.diag(covariance))
    # Guard against zero variance (constant columns)
    d[d == 0] = 1.0
    correlation = covariance / np.outer(d, d)

    # Ensure exact unit diagonal (numerical cleanup)
    np.fill_diagonal(correlation, 1.0)

    return correlation
