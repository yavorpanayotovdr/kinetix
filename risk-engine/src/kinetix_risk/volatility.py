import numpy as np

from kinetix_risk.models import AssetClass

ASSET_CLASS_ORDER = [
    AssetClass.EQUITY,
    AssetClass.FIXED_INCOME,
    AssetClass.FX,
    AssetClass.COMMODITY,
    AssetClass.DERIVATIVE,
]

_ASSET_CLASS_INDEX = {ac: i for i, ac in enumerate(ASSET_CLASS_ORDER)}

DEFAULT_VOLATILITIES: dict[AssetClass, float] = {
    AssetClass.EQUITY: 0.20,
    AssetClass.FIXED_INCOME: 0.06,
    AssetClass.FX: 0.10,
    AssetClass.COMMODITY: 0.25,
    AssetClass.DERIVATIVE: 0.30,
}

CORRELATION_MATRIX = np.array([
    #  EQUITY    FI      FX     COMM    DERIV
    [  1.00,  -0.20,   0.30,   0.40,   0.70],  # EQUITY
    [ -0.20,   1.00,  -0.10,  -0.05,  -0.15],  # FIXED_INCOME
    [  0.30,  -0.10,   1.00,   0.25,   0.20],  # FX
    [  0.40,  -0.05,   0.25,   1.00,   0.35],  # COMMODITY
    [  0.70,  -0.15,   0.20,   0.35,   1.00],  # DERIVATIVE
])


def get_volatility(asset_class: AssetClass) -> float:
    return DEFAULT_VOLATILITIES[asset_class]


def get_correlation_matrix() -> np.ndarray:
    return CORRELATION_MATRIX.copy()


def get_sub_correlation_matrix(asset_classes: list[AssetClass]) -> np.ndarray:
    indices = [_ASSET_CLASS_INDEX[ac] for ac in asset_classes]
    return CORRELATION_MATRIX[np.ix_(indices, indices)].copy()
