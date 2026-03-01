import grpc
import pytest
from concurrent import futures

pytestmark = pytest.mark.integration

from kinetix.common import types_pb2
from kinetix.risk import regulatory_reporting_pb2, regulatory_reporting_pb2_grpc
from kinetix_risk.regulatory_server import RegulatoryReportingServicer


@pytest.fixture
def grpc_channel():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=2))
    regulatory_reporting_pb2_grpc.add_RegulatoryReportingServiceServicer_to_server(
        RegulatoryReportingServicer(), server
    )
    port = server.add_insecure_port("[::]:0")
    server.start()
    channel = grpc.insecure_channel(f"localhost:{port}")
    yield channel
    server.stop(grace=None)
    channel.close()


@pytest.fixture
def stub(grpc_channel):
    return regulatory_reporting_pb2_grpc.RegulatoryReportingServiceStub(grpc_channel)


def _sample_positions():
    return [
        types_pb2.Position(
            portfolio_id=types_pb2.PortfolioId(value="port-1"),
            instrument_id=types_pb2.InstrumentId(value="AAPL"),
            asset_class=types_pb2.EQUITY,
            quantity=100.0,
            market_value=types_pb2.Money(amount="1000000.00", currency="USD"),
        ),
        types_pb2.Position(
            portfolio_id=types_pb2.PortfolioId(value="port-1"),
            instrument_id=types_pb2.InstrumentId(value="US10Y"),
            asset_class=types_pb2.FIXED_INCOME,
            quantity=50.0,
            market_value=types_pb2.Money(amount="500000.00", currency="USD"),
        ),
        types_pb2.Position(
            portfolio_id=types_pb2.PortfolioId(value="port-1"),
            instrument_id=types_pb2.InstrumentId(value="SPX_OPT"),
            asset_class=types_pb2.DERIVATIVE,
            quantity=10.0,
            market_value=types_pb2.Money(amount="400000.00", currency="USD"),
        ),
        types_pb2.Position(
            portfolio_id=types_pb2.PortfolioId(value="port-1"),
            instrument_id=types_pb2.InstrumentId(value="GOLD"),
            asset_class=types_pb2.COMMODITY,
            quantity=20.0,
            market_value=types_pb2.Money(amount="200000.00", currency="USD"),
        ),
    ]


class TestFrtbGrpc:
    def test_calculate_frtb_returns_all_components(self, stub):
        request = regulatory_reporting_pb2.FrtbRequest(
            portfolio_id=types_pb2.PortfolioId(value="port-1"),
            positions=_sample_positions(),
        )
        response = stub.CalculateFrtb(request)
        assert response.sbm is not None
        assert response.drc is not None
        assert response.rrao is not None
        assert response.total_capital_charge > 0
        assert response.calculated_at.seconds > 0

    def test_frtb_total_is_positive(self, stub):
        request = regulatory_reporting_pb2.FrtbRequest(
            portfolio_id=types_pb2.PortfolioId(value="port-1"),
            positions=_sample_positions(),
        )
        response = stub.CalculateFrtb(request)
        assert response.total_capital_charge > 0

    def test_frtb_has_seven_risk_classes(self, stub):
        request = regulatory_reporting_pb2.FrtbRequest(
            portfolio_id=types_pb2.PortfolioId(value="port-1"),
            positions=_sample_positions(),
        )
        response = stub.CalculateFrtb(request)
        assert len(response.sbm.risk_class_charges) == 7


class TestReportGrpc:
    def test_generate_csv_report(self, stub):
        request = regulatory_reporting_pb2.GenerateReportRequest(
            portfolio_id=types_pb2.PortfolioId(value="port-1"),
            positions=_sample_positions(),
            format=regulatory_reporting_pb2.CSV,
        )
        response = stub.GenerateReport(request)
        assert len(response.content) > 0
        assert "Component" in response.content
        assert response.format == regulatory_reporting_pb2.CSV

    def test_generate_xbrl_report(self, stub):
        request = regulatory_reporting_pb2.GenerateReportRequest(
            portfolio_id=types_pb2.PortfolioId(value="port-1"),
            positions=_sample_positions(),
            format=regulatory_reporting_pb2.XBRL,
        )
        response = stub.GenerateReport(request)
        assert len(response.content) > 0
        assert "FRTBReport" in response.content
        assert response.format == regulatory_reporting_pb2.XBRL

    def test_report_has_portfolio_id(self, stub):
        request = regulatory_reporting_pb2.GenerateReportRequest(
            portfolio_id=types_pb2.PortfolioId(value="port-1"),
            positions=_sample_positions(),
            format=regulatory_reporting_pb2.CSV,
        )
        response = stub.GenerateReport(request)
        assert response.portfolio_id == "port-1"
        assert response.generated_at.seconds > 0
