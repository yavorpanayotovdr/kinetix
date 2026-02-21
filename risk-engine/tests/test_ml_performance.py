import time
from concurrent import futures

import grpc
import pytest
import torch

from kinetix.risk import ml_prediction_pb2, ml_prediction_pb2_grpc
from kinetix_risk.ml.data_generator import generate_synthetic_returns, generate_vol_training_data
from kinetix_risk.ml.model_store import ModelStore
from kinetix_risk.ml.vol_predictor import VolatilityLSTM, train_vol_model
from kinetix_risk.ml_server import MLPredictionServicer

ASSET_CLASSES = ["EQUITY", "FIXED_INCOME", "FX", "COMMODITY", "DERIVATIVE"]
ASSET_VOLS = {"EQUITY": 0.20, "FIXED_INCOME": 0.06, "FX": 0.10, "COMMODITY": 0.25, "DERIVATIVE": 0.30}
ASSET_CLASS_ENUMS = {"EQUITY": 1, "FIXED_INCOME": 2, "FX": 3, "COMMODITY": 4, "DERIVATIVE": 5}


@pytest.fixture(scope="module")
def perf_model_store(tmp_path_factory):
    base = tmp_path_factory.mktemp("perf_models")
    store = ModelStore(base)
    for ac, vol in ASSET_VOLS.items():
        X, y = generate_vol_training_data(vol, 400, window_size=60, seed=42)
        model, _ = train_vol_model(X, y, hidden_size=32, num_layers=1, epochs=20, batch_size=32, lr=0.005, seed=42)
        store.save_vol_model(ac, model, "v1")
    return store


@pytest.fixture(scope="module")
def perf_grpc_channel(perf_model_store):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=4))
    ml_prediction_pb2_grpc.add_MLPredictionServiceServicer_to_server(
        MLPredictionServicer(perf_model_store), server
    )
    port = server.add_insecure_port("[::]:0")
    server.start()
    channel = grpc.insecure_channel(f"localhost:{port}")
    yield channel
    server.stop(grace=None)
    channel.close()


@pytest.fixture(scope="module")
def perf_stub(perf_grpc_channel):
    return ml_prediction_pb2_grpc.MLPredictionServiceStub(perf_grpc_channel)


class TestVolPredictionPerformance:
    def test_1000_instruments_batch_under_5s(self, perf_model_store):
        returns_data = generate_synthetic_returns(0.20, 60, seed=42)

        # Group by asset class, build batched tensors
        models = {}
        for ac in ASSET_CLASSES:
            models[ac] = perf_model_store.load_vol_model(ac)

        batch_per_ac = 200  # 1000 / 5 asset classes
        x_batch = torch.FloatTensor([list(returns_data)] * batch_per_ac).unsqueeze(2)

        start = time.time()
        for ac in ASSET_CLASSES:
            with torch.no_grad():
                models[ac](x_batch)
        elapsed = time.time() - start

        assert elapsed < 5.0, f"Direct tensor inference took {elapsed:.2f}s (limit: 5s)"

    def test_1000_instruments_via_grpc_under_5s(self, perf_stub):
        returns_data = list(generate_synthetic_returns(0.20, 60, seed=42))

        requests = []
        for i in range(1000):
            ac_idx = i % 5
            ac_name = ASSET_CLASSES[ac_idx]
            ac_enum = ASSET_CLASS_ENUMS[ac_name]
            requests.append(
                ml_prediction_pb2.VolatilityPredictionRequest(
                    instrument_id=f"INST-{i:04d}",
                    asset_class=ac_enum,
                    returns_window=returns_data,
                )
            )

        batch_request = ml_prediction_pb2.BatchVolatilityRequest(requests=requests)

        start = time.time()
        response = perf_stub.PredictVolatilityBatch(batch_request)
        elapsed = time.time() - start

        assert len(response.predictions) == 1000
        assert all(p.predicted_volatility > 0 for p in response.predictions)
        assert elapsed < 5.0, f"gRPC batch inference took {elapsed:.2f}s (limit: 5s)"
