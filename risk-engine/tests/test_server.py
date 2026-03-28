import grpc
import pytest
from concurrent import futures

pytestmark = pytest.mark.integration

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


def make_var_request(
    book_id="port-1",
    calc_type=risk_calculation_pb2.PARAMETRIC,
    confidence=risk_calculation_pb2.CL_95,
    horizon=1,
    num_sims=1000,
    positions=None,
):
    if positions is None:
        positions = [
            types_pb2.Position(
                book_id=types_pb2.BookId(value=book_id),
                instrument_id=types_pb2.InstrumentId(value="AAPL"),
                asset_class=types_pb2.EQUITY,
                quantity=100.0,
                market_value=types_pb2.Money(amount="150000.00", currency="USD"),
            ),
        ]
    return risk_calculation_pb2.VaRRequest(
        book_id=types_pb2.BookId(value=book_id),
        calculation_type=calc_type,
        confidence_level=confidence,
        time_horizon_days=horizon,
        num_simulations=num_sims,
        positions=positions,
    )


class TestCalculateVaRUnary:
    def test_parametric_var_returns_valid_response(self, stub):
        request = make_var_request()
        response = stub.CalculateVaR(request)

        assert response.book_id.value == "port-1"
        assert response.calculation_type == risk_calculation_pb2.PARAMETRIC
        assert response.confidence_level == risk_calculation_pb2.CL_95
        assert response.var_value > 0
        assert response.expected_shortfall > response.var_value
        assert len(response.component_breakdown) >= 1
        assert response.calculated_at.seconds > 0

    def test_historical_var(self, stub):
        request = make_var_request(calc_type=risk_calculation_pb2.HISTORICAL)
        response = stub.CalculateVaR(request)
        assert response.var_value > 0

    def test_monte_carlo_var(self, stub):
        request = make_var_request(
            calc_type=risk_calculation_pb2.MONTE_CARLO,
            num_sims=5000,
        )
        response = stub.CalculateVaR(request)
        assert response.var_value > 0

    def test_multi_asset_portfolio(self, stub):
        positions = [
            types_pb2.Position(
                book_id=types_pb2.BookId(value="port-1"),
                instrument_id=types_pb2.InstrumentId(value="AAPL"),
                asset_class=types_pb2.EQUITY,
                quantity=100.0,
                market_value=types_pb2.Money(amount="150000.00", currency="USD"),
            ),
            types_pb2.Position(
                book_id=types_pb2.BookId(value="port-1"),
                instrument_id=types_pb2.InstrumentId(value="UST10Y"),
                asset_class=types_pb2.FIXED_INCOME,
                quantity=50.0,
                market_value=types_pb2.Money(amount="500000.00", currency="USD"),
            ),
        ]
        request = make_var_request(positions=positions)
        response = stub.CalculateVaR(request)

        assert len(response.component_breakdown) == 2
        asset_classes = {c.asset_class for c in response.component_breakdown}
        assert types_pb2.EQUITY in asset_classes
        assert types_pb2.FIXED_INCOME in asset_classes

    def test_99_confidence_produces_higher_var(self, stub):
        resp_95 = stub.CalculateVaR(make_var_request(confidence=risk_calculation_pb2.CL_95))
        resp_99 = stub.CalculateVaR(make_var_request(confidence=risk_calculation_pb2.CL_99))
        assert resp_99.var_value > resp_95.var_value


class TestCalculateCrossBookVaR:
    def test_cross_book_var_returns_diversification_benefit(self, stub):
        positions = [
            types_pb2.Position(
                book_id=types_pb2.BookId(value="desk-a"),
                instrument_id=types_pb2.InstrumentId(value="AAPL"),
                asset_class=types_pb2.EQUITY,
                quantity=100.0,
                market_value=types_pb2.Money(amount="150000.00", currency="USD"),
            ),
            types_pb2.Position(
                book_id=types_pb2.BookId(value="desk-b"),
                instrument_id=types_pb2.InstrumentId(value="MSFT"),
                asset_class=types_pb2.EQUITY,
                quantity=200.0,
                market_value=types_pb2.Money(amount="300000.00", currency="USD"),
            ),
        ]
        request = risk_calculation_pb2.CrossBookVaRRequest(
            book_ids=[types_pb2.BookId(value="desk-a"), types_pb2.BookId(value="desk-b")],
            calculation_type=risk_calculation_pb2.PARAMETRIC,
            confidence_level=risk_calculation_pb2.CL_95,
            time_horizon_days=1,
            num_simulations=1000,
            positions=positions,
            portfolio_group_id="firm-wide",
        )
        response = stub.CalculateCrossBookVaR(request)

        assert response.var_value > 0
        assert response.expected_shortfall > response.var_value
        assert response.diversification_benefit >= 0
        assert response.total_standalone_var >= response.var_value
        assert len(response.book_contributions) == 2
        book_ids = {c.book_id.value for c in response.book_contributions}
        assert book_ids == {"desk-a", "desk-b"}

    def test_cross_book_var_each_book_has_standalone_var(self, stub):
        positions = [
            types_pb2.Position(
                book_id=types_pb2.BookId(value="book-1"),
                instrument_id=types_pb2.InstrumentId(value="GOOG"),
                asset_class=types_pb2.EQUITY,
                quantity=50.0,
                market_value=types_pb2.Money(amount="100000.00", currency="USD"),
            ),
            types_pb2.Position(
                book_id=types_pb2.BookId(value="book-2"),
                instrument_id=types_pb2.InstrumentId(value="AMZN"),
                asset_class=types_pb2.EQUITY,
                quantity=75.0,
                market_value=types_pb2.Money(amount="200000.00", currency="USD"),
            ),
        ]
        request = risk_calculation_pb2.CrossBookVaRRequest(
            book_ids=[types_pb2.BookId(value="book-1"), types_pb2.BookId(value="book-2")],
            calculation_type=risk_calculation_pb2.PARAMETRIC,
            confidence_level=risk_calculation_pb2.CL_95,
            time_horizon_days=1,
            num_simulations=1000,
            positions=positions,
        )
        response = stub.CalculateCrossBookVaR(request)

        for contribution in response.book_contributions:
            assert contribution.standalone_var > 0
            assert contribution.var_contribution > 0


class TestCalculateVaRStream:
    def test_streaming_returns_responses_for_each_request(self, stub):
        requests = [
            make_var_request(book_id="port-1"),
            make_var_request(book_id="port-2"),
        ]
        responses = list(stub.CalculateVaRStream(iter(requests)))
        assert len(responses) == 2
        assert responses[0].book_id.value == "port-1"
        assert responses[1].book_id.value == "port-2"

    def test_streaming_each_response_has_valid_var(self, stub):
        requests = [make_var_request() for _ in range(3)]
        responses = list(stub.CalculateVaRStream(iter(requests)))
        for resp in responses:
            assert resp.var_value > 0
            assert resp.expected_shortfall > resp.var_value
