"""Unit tests for the market regime detector.

Rule-based classifier behaviour: NORMAL, ELEVATED_VOL, CRISIS, RECOVERY.
Debounce logic, confidence scoring, degraded-signal fallback, early warnings.
"""

import pytest

from kinetix_risk.regime_detector import (
    MarketRegime,
    RegimeSignals,
    RegimeThresholds,
    RegimeDetector,
    RegimeClassification,
    EarlyWarning,
    classify_regime,
    detect_early_warnings,
    severity_of,
    DEFAULT_THRESHOLDS,
)


# ── Fixtures ───────────────────────────────────────────────────────────────────

THRESHOLDS = RegimeThresholds(
    normal_vol_ceiling=0.15,
    elevated_vol_ceiling=0.25,
    crisis_correlation_floor=0.75,
)

def normal_signals() -> RegimeSignals:
    return RegimeSignals(
        realised_vol_20d=0.10,
        cross_asset_correlation=0.40,
    )

def elevated_vol_signals() -> RegimeSignals:
    return RegimeSignals(
        realised_vol_20d=0.20,
        cross_asset_correlation=0.50,
    )

def crisis_signals() -> RegimeSignals:
    return RegimeSignals(
        realised_vol_20d=0.30,
        cross_asset_correlation=0.80,
    )

def crisis_vol_only_signals() -> RegimeSignals:
    return RegimeSignals(
        realised_vol_20d=0.30,  # > elevated_vol_ceiling
        cross_asset_correlation=0.50,  # < crisis_correlation_floor -> not crisis
    )


# ── Rule-based classification ───────────────────────────────────────────────────

@pytest.mark.unit
def test_classify_returns_normal_when_vol_and_correlation_are_low():
    result = classify_regime(normal_signals(), THRESHOLDS)
    assert result == MarketRegime.NORMAL


@pytest.mark.unit
def test_classify_returns_elevated_vol_when_vol_exceeds_normal_ceiling():
    result = classify_regime(elevated_vol_signals(), THRESHOLDS)
    assert result == MarketRegime.ELEVATED_VOL


@pytest.mark.unit
def test_classify_returns_crisis_when_vol_and_correlation_both_breach():
    result = classify_regime(crisis_signals(), THRESHOLDS)
    assert result == MarketRegime.CRISIS


@pytest.mark.unit
def test_classify_elevated_vol_when_high_vol_but_low_correlation():
    """High vol without high correlation is ELEVATED_VOL, not CRISIS."""
    result = classify_regime(crisis_vol_only_signals(), THRESHOLDS)
    assert result == MarketRegime.ELEVATED_VOL


@pytest.mark.unit
def test_classify_normal_when_vol_exactly_at_ceiling():
    signals = RegimeSignals(
        realised_vol_20d=0.15,  # == normal_vol_ceiling, not strictly greater
        cross_asset_correlation=0.40,
    )
    result = classify_regime(signals, THRESHOLDS)
    assert result == MarketRegime.NORMAL


@pytest.mark.unit
def test_classify_elevated_vol_when_vol_strictly_above_normal_ceiling():
    signals = RegimeSignals(
        realised_vol_20d=0.151,
        cross_asset_correlation=0.40,
    )
    result = classify_regime(signals, THRESHOLDS)
    assert result == MarketRegime.ELEVATED_VOL


# ── Severity ordering ──────────────────────────────────────────────────────────

@pytest.mark.unit
def test_severity_ordering_is_correct():
    assert severity_of(MarketRegime.NORMAL) == 0
    assert severity_of(MarketRegime.RECOVERY) == 1
    assert severity_of(MarketRegime.ELEVATED_VOL) == 2
    assert severity_of(MarketRegime.CRISIS) == 3


@pytest.mark.unit
def test_crisis_has_higher_severity_than_elevated_vol():
    assert severity_of(MarketRegime.CRISIS) > severity_of(MarketRegime.ELEVATED_VOL)


@pytest.mark.unit
def test_elevated_vol_has_higher_severity_than_normal():
    assert severity_of(MarketRegime.ELEVATED_VOL) > severity_of(MarketRegime.NORMAL)


# ── Debounce: escalation requires 3 consecutive, de-escalation requires 1 ──────

@pytest.mark.unit
def test_escalation_to_crisis_confirmed_after_3_consecutive_observations():
    detector = RegimeDetector(THRESHOLDS, escalation_debounce=3, de_escalation_debounce=1)

    result1 = detector.observe(crisis_signals())
    assert result1.regime == MarketRegime.NORMAL   # still in prior confirmed state
    assert not result1.is_confirmed

    result2 = detector.observe(crisis_signals())
    assert result2.regime == MarketRegime.NORMAL
    assert not result2.is_confirmed

    result3 = detector.observe(crisis_signals())
    assert result3.regime == MarketRegime.CRISIS
    assert result3.is_confirmed
    assert result3.consecutive_observations == 3


@pytest.mark.unit
def test_single_vol_spike_does_not_trigger_escalation():
    detector = RegimeDetector(THRESHOLDS, escalation_debounce=3, de_escalation_debounce=1)

    detector.observe(crisis_signals())  # obs 1 toward crisis
    result = detector.observe(normal_signals())  # reset
    assert result.regime == MarketRegime.NORMAL
    assert result.consecutive_observations == 0


@pytest.mark.unit
def test_de_escalation_from_crisis_to_normal_requires_only_1_observation():
    detector = RegimeDetector(THRESHOLDS, escalation_debounce=3, de_escalation_debounce=1)

    # Escalate to CRISIS
    for _ in range(3):
        detector.observe(crisis_signals())

    # A single NORMAL observation should de-escalate
    result = detector.observe(normal_signals())
    assert result.regime == MarketRegime.NORMAL
    assert result.is_confirmed


@pytest.mark.unit
def test_observation_counter_resets_when_same_as_current_regime():
    """If classified regime equals current confirmed regime, counter resets to 0."""
    detector = RegimeDetector(THRESHOLDS, escalation_debounce=3, de_escalation_debounce=1)

    # Start in NORMAL; observe NORMAL — counter stays at 0
    result = detector.observe(normal_signals())
    assert result.regime == MarketRegime.NORMAL
    assert result.consecutive_observations == 0


@pytest.mark.unit
def test_regime_oscillation_does_not_thrash_during_debounce():
    detector = RegimeDetector(THRESHOLDS, escalation_debounce=3, de_escalation_debounce=1)

    # Two crisis observations, then one normal — back to 0, no transition
    detector.observe(crisis_signals())   # pending_count=1
    detector.observe(crisis_signals())   # pending_count=2
    result = detector.observe(normal_signals())  # reset, confirmed NORMAL
    assert result.regime == MarketRegime.NORMAL
    assert result.is_confirmed


@pytest.mark.unit
def test_regime_confirmed_false_during_debounce_window():
    detector = RegimeDetector(THRESHOLDS, escalation_debounce=3, de_escalation_debounce=1)

    result = detector.observe(crisis_signals())  # pending obs 1
    assert result.is_confirmed is False
    assert result.consecutive_observations == 1

    result = detector.observe(crisis_signals())  # pending obs 2
    assert result.is_confirmed is False
    assert result.consecutive_observations == 2


# ── Confidence scoring ─────────────────────────────────────────────────────────

@pytest.mark.unit
def test_crisis_confidence_is_above_neutral_when_signals_exceed_thresholds():
    """Confidence for crisis signals should be above 0.5 (better than coin flip).

    With vol=0.30 vs ceiling=0.25 (20% over) and corr=0.80 vs floor=0.75 (6.7% over),
    the minimum margin is 6.7%, yielding a confidence above 0.5 via the sigmoid.
    """
    detector = RegimeDetector(THRESHOLDS)
    for _ in range(3):
        result = detector.observe(crisis_signals())
    assert result.confidence > 0.5


@pytest.mark.unit
def test_crisis_confidence_is_well_above_neutral_for_extreme_crisis_signals():
    """Very extreme signals should produce confidence clearly above 0.5."""
    extreme_signals = RegimeSignals(
        realised_vol_20d=0.60,  # 2.4x elevated ceiling — very extreme
        cross_asset_correlation=0.95,  # well above crisis floor
    )
    detector = RegimeDetector(THRESHOLDS)
    for _ in range(3):
        result = detector.observe(extreme_signals)
    assert result.confidence >= 0.75


@pytest.mark.unit
def test_normal_confidence_is_high_when_signals_well_below_thresholds():
    detector = RegimeDetector(THRESHOLDS)
    result = detector.observe(normal_signals())
    assert result.confidence >= 0.8


# ── Degraded signals ───────────────────────────────────────────────────────────

@pytest.mark.unit
def test_degraded_signals_flag_set_when_optional_fields_are_none():
    signals = RegimeSignals(
        realised_vol_20d=0.10,
        cross_asset_correlation=0.40,
        credit_spread_bps=None,
        pnl_volatility=None,
    )
    detector = RegimeDetector(THRESHOLDS)
    result = detector.observe(signals)
    assert result.degraded_inputs is True


@pytest.mark.unit
def test_no_degradation_flag_when_all_signals_present():
    signals = RegimeSignals(
        realised_vol_20d=0.10,
        cross_asset_correlation=0.40,
        credit_spread_bps=50.0,
        pnl_volatility=0.05,
    )
    detector = RegimeDetector(THRESHOLDS)
    result = detector.observe(signals)
    assert result.degraded_inputs is False


@pytest.mark.unit
def test_degraded_crisis_signals_use_two_factor_model():
    """With only vol + correlation available, classification still works correctly."""
    signals = RegimeSignals(
        realised_vol_20d=0.30,
        cross_asset_correlation=0.80,
        credit_spread_bps=None,
        pnl_volatility=None,
    )
    result = classify_regime(signals, THRESHOLDS)
    # Two-factor model: vol > elevated_ceiling AND correlation > crisis_floor -> CRISIS
    assert result == MarketRegime.CRISIS


# ── Early warnings ─────────────────────────────────────────────────────────────

@pytest.mark.unit
def test_early_warning_fires_when_vol_approaches_normal_ceiling():
    """Warning when vol > 80% of normal_vol_ceiling."""
    signals = RegimeSignals(
        realised_vol_20d=0.13,  # 0.13 / 0.15 = 86.7% — above 80% threshold
        cross_asset_correlation=0.40,
    )
    warnings = detect_early_warnings(signals, THRESHOLDS)
    signal_names = [w.signal_name for w in warnings]
    assert "realised_vol" in signal_names


@pytest.mark.unit
def test_no_early_warning_when_vol_well_below_ceiling():
    signals = RegimeSignals(
        realised_vol_20d=0.05,
        cross_asset_correlation=0.30,
    )
    warnings = detect_early_warnings(signals, THRESHOLDS)
    assert all(w.signal_name != "realised_vol" for w in warnings)


@pytest.mark.unit
def test_early_warning_fires_when_correlation_approaches_crisis_floor():
    """Warning when correlation > 80% of crisis_correlation_floor."""
    signals = RegimeSignals(
        realised_vol_20d=0.10,
        cross_asset_correlation=0.65,  # 0.65 / 0.75 = 86.7% — above 80%
    )
    warnings = detect_early_warnings(signals, THRESHOLDS)
    signal_names = [w.signal_name for w in warnings]
    assert "cross_asset_correlation" in signal_names


@pytest.mark.unit
def test_early_warning_proximity_pct_computed_correctly():
    signals = RegimeSignals(
        realised_vol_20d=0.13,
        cross_asset_correlation=0.40,
    )
    warnings = detect_early_warnings(signals, THRESHOLDS)
    vol_warning = next(w for w in warnings if w.signal_name == "realised_vol")
    # proximity_pct = current / threshold * 100
    expected_proximity = (0.13 / 0.15) * 100
    assert abs(vol_warning.proximity_pct - expected_proximity) < 0.1


# ── IsolationForest correlation anomaly ───────────────────────────────────────

@pytest.mark.unit
def test_correlation_anomaly_score_is_non_negative():
    import numpy as np
    from kinetix_risk.regime_detector import compute_correlation_anomaly_score

    current = np.array([[1.0, 0.9], [0.9, 1.0]])
    historical_avg = np.array([[1.0, 0.3], [0.3, 1.0]])
    score = compute_correlation_anomaly_score(current, historical_avg)
    assert score >= 0.0


@pytest.mark.unit
def test_correlation_anomaly_score_zero_when_identical():
    import numpy as np
    from kinetix_risk.regime_detector import compute_correlation_anomaly_score

    mat = np.array([[1.0, 0.5], [0.5, 1.0]])
    score = compute_correlation_anomaly_score(mat, mat)
    assert score == pytest.approx(0.0, abs=1e-10)


@pytest.mark.unit
def test_correlation_anomaly_higher_when_more_divergent():
    import numpy as np
    from kinetix_risk.regime_detector import compute_correlation_anomaly_score

    historical = np.array([[1.0, 0.3], [0.3, 1.0]])
    mild = np.array([[1.0, 0.5], [0.5, 1.0]])
    severe = np.array([[1.0, 0.9], [0.9, 1.0]])

    mild_score = compute_correlation_anomaly_score(mild, historical)
    severe_score = compute_correlation_anomaly_score(severe, historical)
    assert severe_score > mild_score


# ── DEFAULT_THRESHOLDS sanity check ───────────────────────────────────────────

@pytest.mark.unit
def test_default_thresholds_values_are_calibrated():
    assert DEFAULT_THRESHOLDS.normal_vol_ceiling > 0
    assert DEFAULT_THRESHOLDS.elevated_vol_ceiling > DEFAULT_THRESHOLDS.normal_vol_ceiling
    assert 0 < DEFAULT_THRESHOLDS.crisis_correlation_floor < 1


# ── RegimeClassification structure ────────────────────────────────────────────

@pytest.mark.unit
def test_regime_classification_contains_all_fields():
    detector = RegimeDetector(THRESHOLDS)
    result = detector.observe(normal_signals())
    assert isinstance(result, RegimeClassification)
    assert result.regime is not None
    assert 0.0 <= result.confidence <= 1.0
    assert isinstance(result.signals, RegimeSignals)
    assert result.consecutive_observations >= 0
    assert isinstance(result.is_confirmed, bool)
    assert isinstance(result.degraded_inputs, bool)
    assert isinstance(result.early_warnings, list)
