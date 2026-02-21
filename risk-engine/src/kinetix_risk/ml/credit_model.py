import numpy as np
from sklearn.ensemble import GradientBoostingClassifier

FEATURE_NAMES = [
    "leverage_ratio",
    "interest_coverage",
    "debt_to_equity",
    "current_ratio",
    "revenue_growth",
    "volatility_90d",
    "market_value_log",
]

RATING_THRESHOLDS = [
    (0.002, "AAA"),
    (0.005, "AA"),
    (0.01, "A"),
    (0.03, "BBB"),
    (0.07, "BB"),
    (0.15, "B"),
    (0.30, "CCC"),
]


def probability_to_rating(prob: float) -> str:
    for threshold, rating in RATING_THRESHOLDS:
        if prob <= threshold:
            return rating
    return "D"


def generate_credit_training_data(
    n_samples: int, seed: int | None = None
) -> tuple[np.ndarray, np.ndarray]:
    rng = np.random.default_rng(seed)
    X = np.zeros((n_samples, len(FEATURE_NAMES)))
    X[:, 0] = rng.lognormal(mean=0.5, sigma=0.5, size=n_samples)    # leverage_ratio
    X[:, 1] = rng.lognormal(mean=1.5, sigma=0.8, size=n_samples)    # interest_coverage
    X[:, 2] = rng.lognormal(mean=0.3, sigma=0.6, size=n_samples)    # debt_to_equity
    X[:, 3] = rng.lognormal(mean=0.5, sigma=0.3, size=n_samples)    # current_ratio
    X[:, 4] = rng.normal(loc=0.05, scale=0.15, size=n_samples)      # revenue_growth
    X[:, 5] = rng.lognormal(mean=-1.5, sigma=0.5, size=n_samples)   # volatility_90d
    X[:, 6] = rng.normal(loc=10, scale=2, size=n_samples)           # market_value_log

    # Labels via logistic scoring
    score = (
        0.8 * X[:, 0]        # high leverage → default
        - 0.5 * X[:, 1]      # high coverage → safe
        + 0.6 * X[:, 2]      # high D/E → default
        - 0.4 * X[:, 3]      # high current ratio → safe
        - 0.3 * X[:, 4]      # revenue growth → safe
        + 1.0 * X[:, 5]      # high vol → default
        - 0.2 * X[:, 6]      # larger firms → safe
    )
    prob = 1.0 / (1.0 + np.exp(-score))
    y = rng.binomial(1, prob).astype(int)
    return X, y


class CreditDefaultModel:
    def __init__(self, classifier: GradientBoostingClassifier):
        self.classifier = classifier

    def predict_probability(self, X: np.ndarray) -> np.ndarray:
        return self.classifier.predict_proba(X)[:, 1]

    def predict_rating(self, X: np.ndarray) -> list[str]:
        probs = self.predict_probability(X)
        return [probability_to_rating(p) for p in probs]


def train_credit_model(
    X: np.ndarray,
    y: np.ndarray,
    n_estimators: int = 100,
    max_depth: int = 3,
    lr: float = 0.1,
    seed: int | None = None,
) -> CreditDefaultModel:
    clf = GradientBoostingClassifier(
        n_estimators=n_estimators,
        max_depth=max_depth,
        learning_rate=lr,
        random_state=seed,
    )
    clf.fit(X, y)
    return CreditDefaultModel(clf)
