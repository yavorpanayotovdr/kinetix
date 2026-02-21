from concurrent import futures

import grpc
import pytest

from kinetix.risk import ml_prediction_pb2, ml_prediction_pb2_grpc
from kinetix_risk.ml.credit_model import generate_credit_training_data, train_credit_model
from kinetix_risk.ml.data_generator import generate_vol_training_data
from kinetix_risk.ml.model_store import ModelStore
from kinetix_risk.ml.vol_predictor import train_vol_model
from kinetix_risk.ml_server import MLPredictionServicer


@pytest.fixture(scope="module")
def credit_model_store(tmp_path_factory):
    base = tmp_path_factory.mktemp("models")
    store = ModelStore(base)
    # Train and save credit model
    X, y = generate_credit_training_data(2000, seed=42)
    model = train_credit_model(X, y, n_estimators=100, seed=42)
    store.save_credit_model(model, "v1")
    # Also need a vol model for the servicer (it's a shared servicer)
    X_vol, y_vol = generate_vol_training_data(0.20, 300, window_size=60, seed=42)
    vol_model, _ = train_vol_model(X_vol, y_vol, hidden_size=32, num_layers=1, epochs=10, batch_size=32, lr=0.005, seed=42)
    store.save_vol_model("EQUITY", vol_model, "v1")
    return store


@pytest.fixture(scope="module")
def credit_grpc_channel(credit_model_store):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=2))
    ml_prediction_pb2_grpc.add_MLPredictionServiceServicer_to_server(
        MLPredictionServicer(credit_model_store), server
    )
    port = server.add_insecure_port("[::]:0")
    server.start()
    channel = grpc.insecure_channel(f"localhost:{port}")
    yield channel
    server.stop(grace=None)
    channel.close()


@pytest.fixture(scope="module")
def credit_stub(credit_grpc_channel):
    return ml_prediction_pb2_grpc.MLPredictionServiceStub(credit_grpc_channel)


class TestCreditScoring:
    def test_score_returns_valid_probability(self, credit_stub):
        request = ml_prediction_pb2.CreditScoreRequest(
            issuer_id="SAFE-CORP",
            leverage_ratio=0.5,
            interest_coverage=8.0,
            debt_to_equity=0.3,
            current_ratio=2.0,
            revenue_growth=0.10,
            volatility_90d=0.15,
            market_value_log=12.0,
        )
        response = credit_stub.ScoreCredit(request)
        assert 0 <= response.default_probability <= 1
        assert response.rating in ("AAA", "AA", "A", "BBB", "BB", "B", "CCC", "D")

    def test_risky_issuer_has_higher_default_prob(self, credit_stub):
        safe = ml_prediction_pb2.CreditScoreRequest(
            issuer_id="SAFE-CORP",
            leverage_ratio=0.3,
            interest_coverage=10.0,
            debt_to_equity=0.2,
            current_ratio=3.0,
            revenue_growth=0.15,
            volatility_90d=0.10,
            market_value_log=13.0,
        )
        risky = ml_prediction_pb2.CreditScoreRequest(
            issuer_id="RISKY-CORP",
            leverage_ratio=5.0,
            interest_coverage=0.5,
            debt_to_equity=4.0,
            current_ratio=0.5,
            revenue_growth=-0.20,
            volatility_90d=0.80,
            market_value_log=7.0,
        )
        safe_resp = credit_stub.ScoreCredit(safe)
        risky_resp = credit_stub.ScoreCredit(risky)
        assert risky_resp.default_probability > safe_resp.default_probability
