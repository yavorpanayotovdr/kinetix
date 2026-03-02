import grpc
import pytest
from concurrent import futures
from unittest.mock import patch

from kinetix.common import types_pb2
from kinetix.risk import risk_calculation_pb2, risk_calculation_pb2_grpc
from kinetix_risk.server import RiskCalculationServicer


@pytest.fixture
def grpc_channel():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=2))
    risk_calculation_pb2_grpc.add_RiskCalculationServiceServicer_to_server(
        RiskCalculationServicer(), server
    )
    port = server.add_insecure_port("[::]:0")
    server.start()
    channel = grpc.insecure_channel(f"localhost:{port}")
    yield channel
    server.stop(grace=None)
    channel.close()


@pytest.fixture
def stub(grpc_channel):
    return risk_calculation_pb2_grpc.RiskCalculationServiceStub(grpc_channel)


def _make_var_request(portfolio_id="port-err"):
    return risk_calculation_pb2.VaRRequest(
        portfolio_id=types_pb2.PortfolioId(value=portfolio_id),
        calculation_type=risk_calculation_pb2.PARAMETRIC,
        confidence_level=risk_calculation_pb2.CL_95,
        time_horizon_days=1,
        num_simulations=1000,
        positions=[
            types_pb2.Position(
                portfolio_id=types_pb2.PortfolioId(value=portfolio_id),
                instrument_id=types_pb2.InstrumentId(value="AAPL"),
                asset_class=types_pb2.EQUITY,
                quantity=100.0,
                market_value=types_pb2.Money(amount="150000.00", currency="USD"),
            ),
        ],
    )


def _make_valuation_request(portfolio_id="port-err"):
    return risk_calculation_pb2.ValuationRequest(
        portfolio_id=types_pb2.PortfolioId(value=portfolio_id),
        calculation_type=risk_calculation_pb2.PARAMETRIC,
        confidence_level=risk_calculation_pb2.CL_95,
        time_horizon_days=1,
        num_simulations=1000,
        positions=[
            types_pb2.Position(
                portfolio_id=types_pb2.PortfolioId(value=portfolio_id),
                instrument_id=types_pb2.InstrumentId(value="AAPL"),
                asset_class=types_pb2.EQUITY,
                quantity=100.0,
                market_value=types_pb2.Money(amount="150000.00", currency="USD"),
            ),
        ],
        requested_outputs=[risk_calculation_pb2.VAR],
    )


class TestCalculateVaRErrorHandling:
    def test_returns_internal_on_unexpected_error(self, stub):
        with patch(
            "kinetix_risk.server.calculate_portfolio_var",
            side_effect=RuntimeError("unexpected crash"),
        ):
            with pytest.raises(grpc.RpcError) as exc_info:
                stub.CalculateVaR(_make_var_request())
            assert exc_info.value.code() == grpc.StatusCode.INTERNAL

    def test_returns_invalid_argument_on_value_error(self, stub):
        with patch(
            "kinetix_risk.server.calculate_portfolio_var",
            side_effect=ValueError("correlation matrix is not positive-definite"),
        ):
            with pytest.raises(grpc.RpcError) as exc_info:
                stub.CalculateVaR(_make_var_request())
            assert exc_info.value.code() == grpc.StatusCode.INVALID_ARGUMENT


class TestValuateErrorHandling:
    def test_returns_internal_on_unexpected_error(self, stub):
        with patch(
            "kinetix_risk.server.calculate_valuation",
            side_effect=RuntimeError("unexpected crash"),
        ):
            with pytest.raises(grpc.RpcError) as exc_info:
                stub.Valuate(_make_valuation_request())
            assert exc_info.value.code() == grpc.StatusCode.INTERNAL
