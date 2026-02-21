from dataclasses import dataclass

import numpy as np
from sklearn.ensemble import IsolationForest


@dataclass(frozen=True)
class AnomalyResult:
    is_anomaly: bool
    anomaly_score: float
    metric_value: float


def generate_anomaly_training_data(
    n_samples: int,
    seed: int | None = None,
    mean: float = 0.0,
    std: float = 1.0,
) -> np.ndarray:
    rng = np.random.default_rng(seed)
    return rng.normal(loc=mean, scale=std, size=(n_samples, 1))


class AnomalyDetector:
    def __init__(self, model: IsolationForest):
        self.model = model

    def detect(self, values: list[float]) -> list[AnomalyResult]:
        X = np.array(values).reshape(-1, 1)
        predictions = self.model.predict(X)
        scores = self.model.score_samples(X)
        results = []
        for i, (pred, score, val) in enumerate(zip(predictions, scores, values)):
            results.append(AnomalyResult(
                is_anomaly=bool(pred == -1),
                anomaly_score=float(score),
                metric_value=float(val),
            ))
        return results

    def detect_single(self, value: float) -> AnomalyResult:
        return self.detect([value])[0]


def train_anomaly_detector(
    training_data: np.ndarray,
    contamination: float = 0.05,
    n_estimators: int = 100,
    seed: int | None = None,
) -> AnomalyDetector:
    model = IsolationForest(
        n_estimators=n_estimators,
        contamination=contamination,
        random_state=seed,
    )
    model.fit(training_data)
    return AnomalyDetector(model)
