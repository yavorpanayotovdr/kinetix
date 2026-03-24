from collections import defaultdict
from pathlib import Path

import torch
from google.protobuf.timestamp_pb2 import Timestamp

from kinetix.risk import ml_prediction_pb2, ml_prediction_pb2_grpc
from kinetix_risk.ml.model_store import ModelStore
from kinetix_risk.regime_detector import (
    MarketRegime,
    RegimeDetector,
    RegimeSignals,
    RegimeThresholds,
)

# Proto AssetClass enum value → string key mapping
_ASSET_CLASS_NAMES = {
    1: "EQUITY",
    2: "FIXED_INCOME",
    3: "FX",
    4: "COMMODITY",
    5: "DERIVATIVE",
}


class MLPredictionServicer(ml_prediction_pb2_grpc.MLPredictionServiceServicer):
    def __init__(self, model_store: ModelStore):
        self.model_store = model_store

    def _predict_vol(self, asset_class_name: str, returns_window: list[float], model_version: str | None) -> tuple[float, str]:
        version = model_version if model_version else None
        model = self.model_store.load_vol_model(asset_class_name, version=version)
        x = torch.FloatTensor(returns_window).unsqueeze(0).unsqueeze(2)
        with torch.no_grad():
            pred = model(x)
        manifest = self.model_store._load_manifest()
        used_version = model_version if model_version else manifest.get(f"vol_{asset_class_name}", {}).get("latest", "unknown")
        return pred.item(), used_version

    def PredictVolatility(self, request, context):
        ac_name = _ASSET_CLASS_NAMES.get(request.asset_class, "EQUITY")
        version_str = request.model_version if request.model_version else None
        predicted_vol, used_version = self._predict_vol(ac_name, list(request.returns_window), version_str)
        now = Timestamp()
        now.GetCurrentTime()
        return ml_prediction_pb2.VolatilityPredictionResponse(
            instrument_id=request.instrument_id,
            asset_class=request.asset_class,
            predicted_volatility=predicted_vol,
            model_version=used_version,
            predicted_at=now,
        )

    def PredictVolatilityBatch(self, request, context):
        # Group by asset class for batched tensor forward pass
        groups: dict[int, list[tuple[int, ml_prediction_pb2.VolatilityPredictionRequest]]] = defaultdict(list)
        for idx, req in enumerate(request.requests):
            groups[req.asset_class].append((idx, req))

        results = [None] * len(request.requests)

        for ac_enum, items in groups.items():
            ac_name = _ASSET_CLASS_NAMES.get(ac_enum, "EQUITY")
            first_req = items[0][1]
            version_str = first_req.model_version if first_req.model_version else None
            model = self.model_store.load_vol_model(ac_name, version=version_str)

            manifest = self.model_store._load_manifest()
            used_version = version_str if version_str else manifest.get(f"vol_{ac_name}", {}).get("latest", "unknown")

            # Build batched tensor
            batch_data = []
            for _, req in items:
                batch_data.append(list(req.returns_window))
            x = torch.FloatTensor(batch_data).unsqueeze(2)

            with torch.no_grad():
                preds = model(x)

            now = Timestamp()
            now.GetCurrentTime()
            for i, (orig_idx, req) in enumerate(items):
                results[orig_idx] = ml_prediction_pb2.VolatilityPredictionResponse(
                    instrument_id=req.instrument_id,
                    asset_class=req.asset_class,
                    predicted_volatility=preds[i].item(),
                    model_version=used_version,
                    predicted_at=now,
                )

        return ml_prediction_pb2.BatchVolatilityResponse(predictions=results)

    def ScoreCredit(self, request, context):
        import numpy as np
        from kinetix_risk.ml.credit_model import probability_to_rating

        credit_model = self.model_store.load_credit_model()
        features = np.array([[
            request.leverage_ratio,
            request.interest_coverage,
            request.debt_to_equity,
            request.current_ratio,
            request.revenue_growth,
            request.volatility_90d,
            request.market_value_log,
        ]])
        prob = credit_model.predict_probability(features)[0]
        rating = probability_to_rating(prob)

        manifest = self.model_store._load_manifest()
        used_version = manifest.get("credit", {}).get("latest", "unknown")
        now = Timestamp()
        now.GetCurrentTime()
        return ml_prediction_pb2.CreditScoreResponse(
            issuer_id=request.issuer_id,
            default_probability=prob,
            rating=rating,
            model_version=used_version,
            scored_at=now,
        )

    def DetectAnomaly(self, request, context):
        detector = self.model_store.load_anomaly_detector()
        results = detector.detect(list(request.metric_values))
        proto_results = [
            ml_prediction_pb2.AnomalyResult(
                is_anomaly=r.is_anomaly,
                anomaly_score=r.anomaly_score,
                metric_value=r.metric_value,
            )
            for r in results
        ]
        return ml_prediction_pb2.AnomalyDetectionResponse(
            metric_name=request.metric_name,
            results=proto_results,
        )

    def DetectRegime(self, request, context):
        """Stateless regime classification with debounce evaluation.

        The orchestrator owns persistent debounce state and passes it in every
        request. This method classifies the current signals, applies the debounce
        rules against the passed-in state, and returns the result.

        Debounce state passed in:
          current_regime: the confirmed regime active in the orchestrator.
          consecutive_observations: how many consecutive observations of the
            candidate (non-current) regime have already been seen, including
            the one that is happening now.

        This is a pure, side-effect-free evaluation. The orchestrator updates
        its own state based on the returned is_confirmed / consecutive_observations.
        """
        from kinetix_risk.regime_detector import (
            classify_regime as _classify,
            _classify_confidence,
            detect_early_warnings,
            severity_of,
        )

        signals_proto = request.signals
        thresholds_proto = request.thresholds

        signals = RegimeSignals(
            realised_vol_20d=signals_proto.realised_vol_20d,
            cross_asset_correlation=signals_proto.cross_asset_correlation,
            credit_spread_bps=(
                signals_proto.credit_spread_bps if signals_proto.credit_spread_present else None
            ),
            pnl_volatility=(
                signals_proto.pnl_volatility if signals_proto.pnl_volatility_present else None
            ),
        )

        thresholds = RegimeThresholds(
            normal_vol_ceiling=thresholds_proto.normal_vol_ceiling,
            elevated_vol_ceiling=thresholds_proto.elevated_vol_ceiling,
            crisis_correlation_floor=thresholds_proto.crisis_correlation_floor,
        )

        escalation_debounce = request.escalation_debounce or 3
        de_escalation_debounce = request.de_escalation_debounce or 1
        consecutive_in = request.consecutive_observations

        try:
            current_confirmed = MarketRegime(request.current_regime) if request.current_regime else MarketRegime.NORMAL
        except ValueError:
            current_confirmed = MarketRegime.NORMAL

        classified = _classify(signals, thresholds)
        degraded = signals.credit_spread_bps is None or signals.pnl_volatility is None
        early_warnings = detect_early_warnings(signals, thresholds)

        if classified == current_confirmed:
            # Same as current confirmed regime — no transition, reset counter
            confidence = _classify_confidence(current_confirmed, signals, thresholds)
            result_regime = current_confirmed
            is_confirmed = True
            out_consecutive = 0
        else:
            # Candidate differs from confirmed — apply debounce
            is_escalation = severity_of(classified) > severity_of(current_confirmed)
            required = escalation_debounce if is_escalation else de_escalation_debounce

            if consecutive_in >= required:
                # Debounce met — transition is confirmed
                result_regime = classified
                is_confirmed = True
                out_consecutive = consecutive_in
            else:
                # Still pending
                result_regime = current_confirmed
                is_confirmed = False
                out_consecutive = consecutive_in

            confidence = _classify_confidence(result_regime, signals, thresholds)

        now = Timestamp()
        now.GetCurrentTime()

        proto_warnings = [
            ml_prediction_pb2.EarlyWarningProto(
                signal_name=w.signal_name,
                current_value=w.current_value,
                threshold=w.threshold,
                proximity_pct=w.proximity_pct,
                message=w.message,
            )
            for w in early_warnings
        ]

        return ml_prediction_pb2.RegimeDetectionResponse(
            regime=result_regime.value,
            confidence=confidence,
            is_confirmed=is_confirmed,
            consecutive_observations=out_consecutive,
            degraded_inputs=degraded,
            early_warnings=proto_warnings,
            detected_at=now,
            correlation_anomaly_score=0.0,  # populated by orchestrator when matrix is available
        )
