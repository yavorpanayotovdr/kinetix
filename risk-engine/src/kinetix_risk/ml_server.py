from collections import defaultdict
from pathlib import Path

import torch
from google.protobuf.timestamp_pb2 import Timestamp

from kinetix.risk import ml_prediction_pb2, ml_prediction_pb2_grpc
from kinetix_risk.ml.model_store import ModelStore

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
        import grpc as _grpc
        context.set_code(_grpc.StatusCode.UNIMPLEMENTED)
        context.set_details("Anomaly detection not yet implemented")
        return ml_prediction_pb2.AnomalyDetectionResponse()
