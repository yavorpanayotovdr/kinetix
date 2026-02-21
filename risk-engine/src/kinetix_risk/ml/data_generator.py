import numpy as np


def generate_synthetic_returns(
    annualized_vol: float,
    num_days: int,
    drift: float = 0.0,
    seed: int | None = None,
) -> np.ndarray:
    rng = np.random.default_rng(seed)
    daily_vol = annualized_vol / np.sqrt(252)
    daily_drift = drift / 252
    returns = rng.normal(loc=daily_drift, scale=daily_vol, size=num_days)
    return returns


def generate_vol_training_data(
    annualized_vol: float,
    num_days: int,
    window_size: int = 60,
    seed: int | None = None,
) -> tuple[np.ndarray, np.ndarray]:
    returns = generate_synthetic_returns(annualized_vol, num_days, seed=seed)
    num_samples = num_days - window_size
    X = np.zeros((num_samples, window_size, 1))
    y = np.zeros(num_samples)
    for i in range(num_samples):
        window = returns[i : i + window_size]
        X[i, :, 0] = window
        y[i] = np.std(window) * np.sqrt(252)
    return X, y
