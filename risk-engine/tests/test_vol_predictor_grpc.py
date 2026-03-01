from concurrent import futures
from pathlib import Path

import grpc
import pytest

pytestmark = pytest.mark.integration

from kinetix.risk import ml_prediction_pb2, ml_prediction_pb2_grpc
from kinetix_risk.ml.data_generator import generate_vol_training_data
from kinetix_risk.ml.model_store import ModelStore
from kinetix_risk.ml.vol_predictor import train_vol_model
from kinetix_risk.ml_server import MLPredictionServicer

ASSET_VOLS = {
    "EQUITY": 0.20,
    "FIXED_INCOME": 0.06,
    "FX": 0.10,
    "COMMODITY": 0.25,
    "DERIVATIVE": 0.30,
}

ASSET_CLASS_ENUMS = {
    "EQUITY": 1,
    "FIXED_INCOME": 2,
    "FX": 3,
    "COMMODITY": 4,
    "DERIVATIVE": 5,
}


@pytest.fixture(scope="module")
def trained_model_store(tmp_path_factory):
    base = tmp_path_factory.mktemp("models")
    store = ModelStore(base)
    for ac, vol in ASSET_VOLS.items():
        X, y = generate_vol_training_data(vol, 400, window_size=60, seed=42)
        model, _ = train_vol_model(X, y, hidden_size=32, num_layers=1, epochs=20, batch_size=32, lr=0.005, seed=42)
        store.save_vol_model(ac, model, "v1")
    return store


@pytest.fixture(scope="module")
def ml_grpc_channel(trained_model_store):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=2))
    ml_prediction_pb2_grpc.add_MLPredictionServiceServicer_to_server(
        MLPredictionServicer(trained_model_store), server
    )
    port = server.add_insecure_port("[::]:0")
    server.start()
    channel = grpc.insecure_channel(f"localhost:{port}")
    yield channel
    server.stop(grace=None)
    channel.close()


@pytest.fixture(scope="module")
def ml_stub(ml_grpc_channel):
    return ml_prediction_pb2_grpc.MLPredictionServiceStub(ml_grpc_channel)


@pytest.fixture(scope="module")
def equity_returns():
    from kinetix_risk.ml.data_generator import generate_synthetic_returns
    return list(generate_synthetic_returns(0.20, 60, seed=99))


class TestPredictVolatility:
    def test_predict_returns_positive_volatility(self, ml_stub, equity_returns):
        request = ml_prediction_pb2.VolatilityPredictionRequest(
            instrument_id="AAPL",
            asset_class=1,  # EQUITY
            returns_window=equity_returns,
        )
        response = ml_stub.PredictVolatility(request)
        assert response.predicted_volatility > 0

    def test_predict_equity_vol_in_reasonable_range(self, ml_stub, equity_returns):
        request = ml_prediction_pb2.VolatilityPredictionRequest(
            instrument_id="AAPL",
            asset_class=1,  # EQUITY
            returns_window=equity_returns,
        )
        response = ml_stub.PredictVolatility(request)
        assert 0.05 < response.predicted_volatility < 0.60

    def test_batch_prediction(self, ml_stub, equity_returns):
        requests = [
            ml_prediction_pb2.VolatilityPredictionRequest(
                instrument_id=f"INST-{i}",
                asset_class=1,
                returns_window=equity_returns,
            )
            for i in range(3)
        ]
        batch_request = ml_prediction_pb2.BatchVolatilityRequest(requests=requests)
        response = ml_stub.PredictVolatilityBatch(batch_request)
        assert len(response.predictions) == 3
        for pred in response.predictions:
            assert pred.predicted_volatility > 0
