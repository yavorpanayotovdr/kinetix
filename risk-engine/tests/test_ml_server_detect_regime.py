"""Unit tests for MLPredictionServicer.DetectRegime().

Tests the gRPC servicer layer — proto request → regime_detector → proto response.
"""

import pytest

from kinetix.risk import ml_prediction_pb2
from kinetix_risk.ml_server import MLPredictionServicer
from kinetix_risk.ml.model_store import ModelStore
from pathlib import Path
from unittest.mock import MagicMock


def make_servicer() -> MLPredictionServicer:
    model_store = ModelStore(Path("models"))
    return MLPredictionServicer(model_store)


def make_normal_request() -> ml_prediction_pb2.RegimeDetectionRequest:
    signals = ml_prediction_pb2.RegimeSignalsProto(
        realised_vol_20d=0.10,
        cross_asset_correlation=0.40,
        credit_spread_present=True,
        credit_spread_bps=50.0,
        pnl_volatility_present=True,
        pnl_volatility=0.04,
    )
    thresholds = ml_prediction_pb2.RegimeThresholdsProto(
        normal_vol_ceiling=0.15,
        elevated_vol_ceiling=0.25,
        crisis_correlation_floor=0.75,
    )
    return ml_prediction_pb2.RegimeDetectionRequest(
        signals=signals,
        thresholds=thresholds,
        current_regime="NORMAL",
        consecutive_observations=0,
        escalation_debounce=3,
        de_escalation_debounce=1,
    )


def make_crisis_request(consecutive: int = 3) -> ml_prediction_pb2.RegimeDetectionRequest:
    signals = ml_prediction_pb2.RegimeSignalsProto(
        realised_vol_20d=0.30,
        cross_asset_correlation=0.80,
        credit_spread_present=False,
        pnl_volatility_present=False,
    )
    thresholds = ml_prediction_pb2.RegimeThresholdsProto(
        normal_vol_ceiling=0.15,
        elevated_vol_ceiling=0.25,
        crisis_correlation_floor=0.75,
    )
    return ml_prediction_pb2.RegimeDetectionRequest(
        signals=signals,
        thresholds=thresholds,
        current_regime="NORMAL",
        consecutive_observations=consecutive,
        escalation_debounce=3,
        de_escalation_debounce=1,
    )


@pytest.mark.unit
def test_detect_regime_returns_normal_for_calm_market():
    servicer = make_servicer()
    context = MagicMock()
    response = servicer.DetectRegime(make_normal_request(), context)
    assert response.regime == "NORMAL"


@pytest.mark.unit
def test_detect_regime_returns_is_confirmed_true_for_stable_regime():
    servicer = make_servicer()
    context = MagicMock()
    response = servicer.DetectRegime(make_normal_request(), context)
    assert response.is_confirmed is True


@pytest.mark.unit
def test_detect_regime_returns_crisis_when_debounce_met():
    """When current is NORMAL and 3 consecutive crisis observations accumulate, crisis is confirmed."""
    servicer = make_servicer()
    context = MagicMock()

    # Simulate 3 consecutive crisis observations using stateless call with obs=3
    response = servicer.DetectRegime(make_crisis_request(consecutive=3), context)
    assert response.regime == "CRISIS"
    assert response.is_confirmed is True


@pytest.mark.unit
def test_detect_regime_not_confirmed_before_debounce():
    servicer = make_servicer()
    context = MagicMock()

    # Only 2 consecutive — debounce requires 3
    response = servicer.DetectRegime(make_crisis_request(consecutive=2), context)
    assert response.is_confirmed is False


@pytest.mark.unit
def test_detect_regime_sets_degraded_inputs_when_optional_signals_absent():
    servicer = make_servicer()
    context = MagicMock()

    request = ml_prediction_pb2.RegimeDetectionRequest(
        signals=ml_prediction_pb2.RegimeSignalsProto(
            realised_vol_20d=0.10,
            cross_asset_correlation=0.40,
            credit_spread_present=False,
            pnl_volatility_present=False,
        ),
        thresholds=ml_prediction_pb2.RegimeThresholdsProto(
            normal_vol_ceiling=0.15,
            elevated_vol_ceiling=0.25,
            crisis_correlation_floor=0.75,
        ),
        current_regime="NORMAL",
        consecutive_observations=0,
        escalation_debounce=3,
        de_escalation_debounce=1,
    )
    response = servicer.DetectRegime(request, context)
    assert response.degraded_inputs is True


@pytest.mark.unit
def test_detect_regime_no_degradation_when_all_signals_present():
    servicer = make_servicer()
    context = MagicMock()
    response = servicer.DetectRegime(make_normal_request(), context)
    assert response.degraded_inputs is False


@pytest.mark.unit
def test_detect_regime_includes_detected_at_timestamp():
    servicer = make_servicer()
    context = MagicMock()
    response = servicer.DetectRegime(make_normal_request(), context)
    # Timestamp should be set (non-zero seconds)
    assert response.detected_at.seconds > 0


@pytest.mark.unit
def test_detect_regime_confidence_is_between_0_and_1():
    servicer = make_servicer()
    context = MagicMock()
    response = servicer.DetectRegime(make_normal_request(), context)
    assert 0.0 <= response.confidence <= 1.0


@pytest.mark.unit
def test_detect_regime_elevated_vol_when_vol_exceeds_normal_ceiling():
    servicer = make_servicer()
    context = MagicMock()
    request = ml_prediction_pb2.RegimeDetectionRequest(
        signals=ml_prediction_pb2.RegimeSignalsProto(
            realised_vol_20d=0.20,
            cross_asset_correlation=0.50,
            credit_spread_present=True,
            credit_spread_bps=60.0,
            pnl_volatility_present=True,
            pnl_volatility=0.06,
        ),
        thresholds=ml_prediction_pb2.RegimeThresholdsProto(
            normal_vol_ceiling=0.15,
            elevated_vol_ceiling=0.25,
            crisis_correlation_floor=0.75,
        ),
        current_regime="NORMAL",
        consecutive_observations=3,  # already at debounce for de-escalation
        escalation_debounce=3,
        de_escalation_debounce=1,
    )
    response = servicer.DetectRegime(request, context)
    # Elevated vol triggers on first observation (de-escalation debounce=1 from NORMAL)
    # Actually this is escalation from NORMAL → ELEVATED_VOL, needs 3
    # But consecutive_observations=3 → confirmed
    assert response.regime == "ELEVATED_VOL"
    assert response.is_confirmed is True


@pytest.mark.unit
def test_detect_regime_early_warnings_included_when_vol_near_threshold():
    servicer = make_servicer()
    context = MagicMock()
    request = ml_prediction_pb2.RegimeDetectionRequest(
        signals=ml_prediction_pb2.RegimeSignalsProto(
            realised_vol_20d=0.13,  # 86.7% of normal_vol_ceiling
            cross_asset_correlation=0.40,
            credit_spread_present=True,
            credit_spread_bps=50.0,
            pnl_volatility_present=True,
            pnl_volatility=0.04,
        ),
        thresholds=ml_prediction_pb2.RegimeThresholdsProto(
            normal_vol_ceiling=0.15,
            elevated_vol_ceiling=0.25,
            crisis_correlation_floor=0.75,
        ),
        current_regime="NORMAL",
        consecutive_observations=0,
        escalation_debounce=3,
        de_escalation_debounce=1,
    )
    response = servicer.DetectRegime(request, context)
    warning_names = [w.signal_name for w in response.early_warnings]
    assert "realised_vol" in warning_names
