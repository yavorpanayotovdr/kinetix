"""gRPC integration tests for the RunReverseStress endpoint.

Verifies the full path from proto request through StressTestServicer to the
scipy.optimize.minimize solver and back to proto response.
"""
import pytest
import grpc
from concurrent import futures

from kinetix.common import types_pb2
from kinetix.risk import stress_testing_pb2, stress_testing_pb2_grpc
from kinetix_risk.stress_server import StressTestServicer

pytestmark = pytest.mark.integration


@pytest.fixture
def stub():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=2))
    stress_testing_pb2_grpc.add_StressTestServiceServicer_to_server(
        StressTestServicer(), server
    )
    port = server.add_insecure_port("[::]:0")
    server.start()
    channel = grpc.insecure_channel(f"localhost:{port}")
    yield stress_testing_pb2_grpc.StressTestServiceStub(channel)
    server.stop(grace=None)
    channel.close()


def _position(instrument_id: str, market_value: float) -> types_pb2.Position:
    return types_pb2.Position(
        book_id=types_pb2.BookId(value="port-1"),
        instrument_id=types_pb2.InstrumentId(value=instrument_id),
        asset_class=types_pb2.EQUITY,
        quantity=1.0,
        market_value=types_pb2.Money(amount=str(market_value), currency="USD"),
    )


class TestRunReverseStressGrpc:

    def test_single_position_shock_converges(self, stub):
        """For a single $1M equity position, solver finds a shock that achieves target loss."""
        request = stress_testing_pb2.ReverseStressRequest(
            positions=[_position("AAPL", 1_000_000.0)],
            target_loss=100_000.0,
        )
        response = stub.RunReverseStress(request)

        assert response.converged is True
        assert len(response.shocks) == 1
        assert response.shocks[0].instrument_id == "AAPL"
        assert response.shocks[0].shock < 0.0
        assert response.achieved_loss >= response.target_loss * 0.95
        assert response.target_loss == pytest.approx(100_000.0)
        assert response.calculated_at.seconds > 0

    def test_two_positions_balanced_shocks(self, stub):
        """With two equal positions, the solver distributes shocks evenly."""
        request = stress_testing_pb2.ReverseStressRequest(
            positions=[
                _position("AAPL", 1_000_000.0),
                _position("MSFT", 1_000_000.0),
            ],
            target_loss=200_000.0,
        )
        response = stub.RunReverseStress(request)

        assert response.converged is True
        assert len(response.shocks) == 2
        shocks = {s.instrument_id: s.shock for s in response.shocks}
        assert shocks["AAPL"] < 0.0
        assert shocks["MSFT"] < 0.0
        ratio = abs(shocks["AAPL"]) / abs(shocks["MSFT"])
        assert ratio == pytest.approx(1.0, abs=0.3)

    def test_infeasible_target_returns_not_converged(self, stub):
        """Target loss exceeding 100% of portfolio value cannot be achieved."""
        request = stress_testing_pb2.ReverseStressRequest(
            positions=[_position("AAPL", 1_000_000.0)],
            target_loss=1_500_000.0,
            max_shock=-1.0,
        )
        response = stub.RunReverseStress(request)

        assert response.converged is False

    def test_empty_positions_returns_invalid_argument(self, stub):
        """Empty positions list must be rejected with INVALID_ARGUMENT status."""
        request = stress_testing_pb2.ReverseStressRequest(
            positions=[],
            target_loss=100_000.0,
        )
        with pytest.raises(grpc.RpcError) as exc_info:
            stub.RunReverseStress(request)
        assert exc_info.value.code() == grpc.StatusCode.INVALID_ARGUMENT

    def test_non_positive_target_returns_invalid_argument(self, stub):
        """A target_loss of zero or negative is rejected."""
        request = stress_testing_pb2.ReverseStressRequest(
            positions=[_position("AAPL", 1_000_000.0)],
            target_loss=0.0,
        )
        with pytest.raises(grpc.RpcError) as exc_info:
            stub.RunReverseStress(request)
        assert exc_info.value.code() == grpc.StatusCode.INVALID_ARGUMENT
