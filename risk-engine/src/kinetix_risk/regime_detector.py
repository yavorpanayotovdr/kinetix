"""Market regime detector.

Rule-based classifier (not ML — explainable for governance):
  CRISIS:       realised_vol > elevated_vol_ceiling AND cross_asset_correlation > crisis_correlation_floor
  ELEVATED_VOL: realised_vol > normal_vol_ceiling
  NORMAL:       otherwise

The IsolationForest model is used only for the supplementary correlation
anomaly score; it does not drive the regime label.

Debounce:
  escalation (higher severity) → 3 consecutive observations required
  de-escalation (lower severity) → 1 observation sufficient

Severity ordering: normal=0, recovery=1, elevated_vol=2, crisis=3
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from enum import Enum
from typing import Optional

import numpy as np


# ── Enumerations ───────────────────────────────────────────────────────────────

class MarketRegime(str, Enum):
    NORMAL = "NORMAL"
    ELEVATED_VOL = "ELEVATED_VOL"
    CRISIS = "CRISIS"
    RECOVERY = "RECOVERY"


_SEVERITY: dict[MarketRegime, int] = {
    MarketRegime.NORMAL: 0,
    MarketRegime.RECOVERY: 1,
    MarketRegime.ELEVATED_VOL: 2,
    MarketRegime.CRISIS: 3,
}


def severity_of(regime: MarketRegime) -> int:
    """Return the numeric severity for debounce comparisons."""
    return _SEVERITY[regime]


# ── Value types ────────────────────────────────────────────────────────────────

@dataclass(frozen=True)
class RegimeSignals:
    """Raw input signals to the regime classifier.

    realised_vol_20d: 20-day EWMA annualised vol (from price history).
    cross_asset_correlation: average off-diagonal in Ledoit-Wolf matrix.
    credit_spread_bps: CDX IG or equivalent (None if unavailable).
    pnl_volatility: 20-day rolling vol of book P&L (None pre-Direction 1).
    vol_of_vol: vol-of-vol (None if unavailable).
    """
    realised_vol_20d: float
    cross_asset_correlation: float
    credit_spread_bps: Optional[float] = None
    pnl_volatility: Optional[float] = None
    vol_of_vol: Optional[float] = None


@dataclass(frozen=True)
class RegimeThresholds:
    """Regime classifier thresholds.

    normal_vol_ceiling:       ~80th percentile of historical 21-day vol.
    elevated_vol_ceiling:     ~95th percentile.
    crisis_correlation_floor: average off-diagonal > 0.75.
    """
    normal_vol_ceiling: float
    elevated_vol_ceiling: float
    crisis_correlation_floor: float


@dataclass(frozen=True)
class EarlyWarning:
    signal_name: str
    current_value: float
    threshold: float
    proximity_pct: float
    message: str


@dataclass(frozen=True)
class RegimeClassification:
    """Result of one regime detection cycle."""
    regime: MarketRegime
    confidence: float
    signals: RegimeSignals
    consecutive_observations: int
    is_confirmed: bool
    degraded_inputs: bool
    early_warnings: list[EarlyWarning]


# ── Default thresholds (calibrated from 10-year lookback) ─────────────────────

DEFAULT_THRESHOLDS = RegimeThresholds(
    normal_vol_ceiling=0.15,    # ~80th pct of historical 21-day annualised vol
    elevated_vol_ceiling=0.25,  # ~95th pct
    crisis_correlation_floor=0.75,  # average off-diagonal crisis level
)


# ── Pure classification function ───────────────────────────────────────────────

def classify_regime(signals: RegimeSignals, thresholds: RegimeThresholds) -> MarketRegime:
    """Rule-based regime classification.

    CRISIS:       realised_vol > elevated_vol_ceiling AND correlation > crisis_correlation_floor
    ELEVATED_VOL: realised_vol > normal_vol_ceiling
    NORMAL:       otherwise
    """
    vol = signals.realised_vol_20d
    corr = signals.cross_asset_correlation

    if vol > thresholds.elevated_vol_ceiling and corr > thresholds.crisis_correlation_floor:
        return MarketRegime.CRISIS
    if vol > thresholds.normal_vol_ceiling:
        return MarketRegime.ELEVATED_VOL
    return MarketRegime.NORMAL


def _classify_confidence(
    regime: MarketRegime,
    signals: RegimeSignals,
    thresholds: RegimeThresholds,
) -> float:
    """Compute a [0, 1] confidence score.

    Confidence is the normalised distance of the key signal from the
    nearest boundary in the direction of the classified regime.
    Higher distance → higher confidence.
    """
    vol = signals.realised_vol_20d
    corr = signals.cross_asset_correlation

    if regime == MarketRegime.CRISIS:
        # Both vol and correlation must breach — take the minimum margin
        vol_margin = (vol - thresholds.elevated_vol_ceiling) / thresholds.elevated_vol_ceiling
        corr_margin = (corr - thresholds.crisis_correlation_floor) / thresholds.crisis_correlation_floor
        raw = min(vol_margin, corr_margin)
    elif regime == MarketRegime.ELEVATED_VOL:
        raw = (vol - thresholds.normal_vol_ceiling) / thresholds.normal_vol_ceiling
    else:
        # NORMAL: distance below normal_vol_ceiling normalised against ceiling
        raw = (thresholds.normal_vol_ceiling - vol) / thresholds.normal_vol_ceiling

    # Sigmoid mapping so extreme signals approach 1.0 asymptotically
    return float(1.0 / (1.0 + math.exp(-5.0 * raw)))


# ── Early warning detection ────────────────────────────────────────────────────

def detect_early_warnings(
    signals: RegimeSignals,
    thresholds: RegimeThresholds,
) -> list[EarlyWarning]:
    """Detect signals approaching regime transition thresholds.

    Fires when a signal is within 20% of a threshold (proximity >= 80%).
    """
    warnings: list[EarlyWarning] = []

    # Vol approaching normal_vol_ceiling
    vol_proximity = (signals.realised_vol_20d / thresholds.normal_vol_ceiling) * 100.0
    if vol_proximity >= 80.0 and signals.realised_vol_20d <= thresholds.normal_vol_ceiling:
        warnings.append(EarlyWarning(
            signal_name="realised_vol",
            current_value=signals.realised_vol_20d,
            threshold=thresholds.normal_vol_ceiling,
            proximity_pct=vol_proximity,
            message="Realised volatility approaching elevated regime threshold",
        ))

    # Cross-asset correlation approaching crisis floor
    corr_proximity = (signals.cross_asset_correlation / thresholds.crisis_correlation_floor) * 100.0
    if corr_proximity >= 80.0 and signals.cross_asset_correlation <= thresholds.crisis_correlation_floor:
        warnings.append(EarlyWarning(
            signal_name="cross_asset_correlation",
            current_value=signals.cross_asset_correlation,
            threshold=thresholds.crisis_correlation_floor,
            proximity_pct=corr_proximity,
            message="Cross-asset correlation structure shifting toward crisis pattern",
        ))

    return warnings


# ── Correlation anomaly score ──────────────────────────────────────────────────

def compute_correlation_anomaly_score(
    current_correlation: np.ndarray,
    historical_average: np.ndarray,
) -> float:
    """L2 norm of the difference between current and historical average correlation matrices.

    Used to detect structural shifts in cross-asset relationships before they
    appear in VaR. A high score indicates correlation breakdown/flight-to-quality.
    """
    diff = current_correlation - historical_average
    return float(np.linalg.norm(diff, ord="fro"))


# ── Stateful detector with debounce ───────────────────────────────────────────

class RegimeDetector:
    """Stateful regime classifier with debounce.

    Maintains:
      _confirmed_regime: the currently active, confirmed regime.
      _pending_regime:   the candidate regime being built up.
      _pending_count:    how many consecutive observations of _pending_regime.
    """

    def __init__(
        self,
        thresholds: RegimeThresholds = DEFAULT_THRESHOLDS,
        escalation_debounce: int = 3,
        de_escalation_debounce: int = 1,
    ) -> None:
        self._thresholds = thresholds
        self._escalation_debounce = escalation_debounce
        self._de_escalation_debounce = de_escalation_debounce

        self._confirmed_regime = MarketRegime.NORMAL
        self._pending_regime: Optional[MarketRegime] = None
        self._pending_count: int = 0

    def observe(self, signals: RegimeSignals) -> RegimeClassification:
        """Process one detection cycle and return the current classification state."""
        classified = classify_regime(signals, self._thresholds)
        degraded = signals.credit_spread_bps is None or signals.pnl_volatility is None
        early_warnings = detect_early_warnings(signals, self._thresholds)

        if classified == self._confirmed_regime:
            # Same regime — reset any pending escalation/de-escalation counter
            self._pending_regime = None
            self._pending_count = 0
            confidence = _classify_confidence(self._confirmed_regime, signals, self._thresholds)
            return RegimeClassification(
                regime=self._confirmed_regime,
                confidence=confidence,
                signals=signals,
                consecutive_observations=0,
                is_confirmed=True,
                degraded_inputs=degraded,
                early_warnings=early_warnings,
            )

        # New candidate regime
        if self._pending_regime == classified:
            self._pending_count += 1
        else:
            self._pending_regime = classified
            self._pending_count = 1

        is_escalation = severity_of(classified) > severity_of(self._confirmed_regime)
        required = self._escalation_debounce if is_escalation else self._de_escalation_debounce

        if self._pending_count >= required:
            # Transition confirmed
            self._confirmed_regime = classified
            self._pending_regime = None
            confirmed_count = self._pending_count
            self._pending_count = 0
            confidence = _classify_confidence(self._confirmed_regime, signals, self._thresholds)
            return RegimeClassification(
                regime=self._confirmed_regime,
                confidence=confidence,
                signals=signals,
                consecutive_observations=confirmed_count,
                is_confirmed=True,
                degraded_inputs=degraded,
                early_warnings=early_warnings,
            )

        # Debounce not met — report pending state (not yet confirmed)
        confidence = _classify_confidence(self._confirmed_regime, signals, self._thresholds)
        return RegimeClassification(
            regime=self._confirmed_regime,  # still returning the confirmed regime
            confidence=confidence,
            signals=signals,
            consecutive_observations=self._pending_count,
            is_confirmed=False,
            degraded_inputs=degraded,
            early_warnings=early_warnings,
        )
