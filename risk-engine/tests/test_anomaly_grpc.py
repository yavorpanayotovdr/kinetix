from concurrent import futures

import grpc
import pytest

from kinetix.risk import ml_prediction_pb2, ml_prediction_pb2_grpc
from kinetix_risk.ml.anomaly_detector import generate_anomaly_training_data, train_anomaly_detector
from kinetix_risk.ml.data_generator import generate_vol_training_data
from kinetix_risk.ml.model_store import ModelStore
from kinetix_risk.ml.vol_predictor import train_vol_model
from kinetix_risk.ml_server import MLPredictionServicer


@pytest.fixture(scope="module")
def anomaly_model_store(tmp_path_factory):
    base = tmp_path_factory.mktemp("models")
    store = ModelStore(base)
    # Train and save anomaly detector
    data = generate_anomaly_training_data(500, seed=42, mean=0.0, std=1.0)
    detector = train_anomaly_detector(data, contamination=0.05, seed=42)
    store.save_anomaly_detector(detector, "v1")
    # Need a vol model for the shared servicer
    X_vol, y_vol = generate_vol_training_data(0.20, 300, window_size=60, seed=42)
    vol_model, _ = train_vol_model(X_vol, y_vol, hidden_size=32, num_layers=1, epochs=10, batch_size=32, lr=0.005, seed=42)
    store.save_vol_model("EQUITY", vol_model, "v1")
    return store


@pytest.fixture(scope="module")
def anomaly_grpc_channel(anomaly_model_store):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=2))
    ml_prediction_pb2_grpc.add_MLPredictionServiceServicer_to_server(
        MLPredictionServicer(anomaly_model_store), server
    )
    port = server.add_insecure_port("[::]:0")
    server.start()
    channel = grpc.insecure_channel(f"localhost:{port}")
    yield channel
    server.stop(grace=None)
    channel.close()


@pytest.fixture(scope="module")
def anomaly_stub(anomaly_grpc_channel):
    return ml_prediction_pb2_grpc.MLPredictionServiceStub(anomaly_grpc_channel)


class TestAnomalyDetectionGrpc:
    def test_normal_values_not_flagged(self, anomaly_stub):
        request = ml_prediction_pb2.AnomalyDetectionRequest(
            metric_name="var_value",
            metric_values=[0.0, 0.1, -0.1, 0.5, -0.3],
        )
        response = anomaly_stub.DetectAnomaly(request)
        assert len(response.results) == 5
        anomaly_count = sum(1 for r in response.results if r.is_anomaly)
        assert anomaly_count < 3

    def test_extreme_value_flagged(self, anomaly_stub):
        request = ml_prediction_pb2.AnomalyDetectionRequest(
            metric_name="var_value",
            metric_values=[100.0],
        )
        response = anomaly_stub.DetectAnomaly(request)
        assert len(response.results) == 1
        assert response.results[0].is_anomaly is True
