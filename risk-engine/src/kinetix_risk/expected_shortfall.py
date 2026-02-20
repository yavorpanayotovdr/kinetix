import numpy as np

from kinetix_risk.models import ConfidenceLevel


def calculate_expected_shortfall(
    losses: np.ndarray,
    confidence_level: ConfidenceLevel,
) -> float:
    if len(losses) == 0:
        raise ValueError("Cannot calculate ES on empty losses array")
    alpha = confidence_level.value
    var_threshold = np.percentile(losses, alpha * 100)
    tail_losses = losses[losses >= var_threshold]
    return float(np.mean(tail_losses))
