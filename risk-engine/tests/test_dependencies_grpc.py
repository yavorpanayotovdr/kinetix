import grpc
import pytest
from concurrent import futures

from kinetix.common import types_pb2
from kinetix.risk import market_data_dependencies_pb2, market_data_dependencies_pb2_grpc, risk_calculation_pb2
from kinetix_risk.dependencies_server import MarketDataDependenciesServicer


@pytest.fixture
def grpc_channel():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=2))
    market_data_dependencies_pb2_grpc.add_MarketDataDependenciesServiceServicer_to_server(
        MarketDataDependenciesServicer(), server
    )
    port = server.add_insecure_port("[::]:0")
    server.start()
    channel = grpc.insecure_channel(f"localhost:{port}")
    yield channel
    server.stop(grace=None)
    channel.close()


@pytest.fixture
def stub(grpc_channel):
    return market_data_dependencies_pb2_grpc.MarketDataDependenciesServiceStub(grpc_channel)


def _equity_position(instrument_id="AAPL"):
    return types_pb2.Position(
        portfolio_id=types_pb2.PortfolioId(value="port-1"),
        instrument_id=types_pb2.InstrumentId(value=instrument_id),
        asset_class=types_pb2.EQUITY,
        quantity=100.0,
        market_value=types_pb2.Money(amount="1000000.00", currency="USD"),
    )


def _derivative_position(instrument_id="AAPL-C-250-20260620"):
    return types_pb2.Position(
        portfolio_id=types_pb2.PortfolioId(value="port-1"),
        instrument_id=types_pb2.InstrumentId(value=instrument_id),
        asset_class=types_pb2.DERIVATIVE,
        quantity=10.0,
        market_value=types_pb2.Money(amount="50000.00", currency="USD"),
    )


def _fixed_income_position(instrument_id="TBOND-10Y"):
    return types_pb2.Position(
        portfolio_id=types_pb2.PortfolioId(value="port-1"),
        instrument_id=types_pb2.InstrumentId(value=instrument_id),
        asset_class=types_pb2.FIXED_INCOME,
        quantity=100.0,
        market_value=types_pb2.Money(amount="500000.00", currency="USD"),
    )


def _commodity_position(instrument_id="GOLD"):
    return types_pb2.Position(
        portfolio_id=types_pb2.PortfolioId(value="port-1"),
        instrument_id=types_pb2.InstrumentId(value=instrument_id),
        asset_class=types_pb2.COMMODITY,
        quantity=50.0,
        market_value=types_pb2.Money(amount="200000.00", currency="USD"),
    )


class TestDiscoverDependenciesGrpc:
    def test_empty_positions_returns_empty(self, stub):
        request = market_data_dependencies_pb2.DataDependenciesRequest(
            positions=[],
            calculation_type=risk_calculation_pb2.PARAMETRIC,
            confidence_level=risk_calculation_pb2.CL_95,
        )
        response = stub.DiscoverDependencies(request)
        assert len(response.dependencies) == 0

    def test_single_equity_returns_dependencies(self, stub):
        request = market_data_dependencies_pb2.DataDependenciesRequest(
            positions=[_equity_position()],
            calculation_type=risk_calculation_pb2.PARAMETRIC,
            confidence_level=risk_calculation_pb2.CL_95,
        )
        response = stub.DiscoverDependencies(request)
        assert len(response.dependencies) >= 2

        data_types = {d.data_type for d in response.dependencies}
        assert market_data_dependencies_pb2.SPOT_PRICE in data_types
        assert market_data_dependencies_pb2.HISTORICAL_PRICES in data_types

    def test_derivative_returns_vol_surface_and_risk_free_rate(self, stub):
        request = market_data_dependencies_pb2.DataDependenciesRequest(
            positions=[_derivative_position()],
            calculation_type=risk_calculation_pb2.MONTE_CARLO,
            confidence_level=risk_calculation_pb2.CL_99,
        )
        response = stub.DiscoverDependencies(request)

        data_types = {d.data_type for d in response.dependencies}
        assert market_data_dependencies_pb2.SPOT_PRICE in data_types
        assert market_data_dependencies_pb2.VOLATILITY_SURFACE in data_types
        assert market_data_dependencies_pb2.RISK_FREE_RATE in data_types

    def test_mixed_portfolio_includes_correlation_matrix(self, stub):
        request = market_data_dependencies_pb2.DataDependenciesRequest(
            positions=[_equity_position(), _commodity_position()],
            calculation_type=risk_calculation_pb2.PARAMETRIC,
            confidence_level=risk_calculation_pb2.CL_95,
        )
        response = stub.DiscoverDependencies(request)

        data_types = {d.data_type for d in response.dependencies}
        assert market_data_dependencies_pb2.CORRELATION_MATRIX in data_types

    def test_single_asset_class_no_correlation_matrix(self, stub):
        request = market_data_dependencies_pb2.DataDependenciesRequest(
            positions=[_equity_position("AAPL"), _equity_position("GOOGL")],
            calculation_type=risk_calculation_pb2.PARAMETRIC,
            confidence_level=risk_calculation_pb2.CL_95,
        )
        response = stub.DiscoverDependencies(request)

        data_types = {d.data_type for d in response.dependencies}
        assert market_data_dependencies_pb2.CORRELATION_MATRIX not in data_types

    def test_dependency_fields_are_populated(self, stub):
        request = market_data_dependencies_pb2.DataDependenciesRequest(
            positions=[_equity_position()],
            calculation_type=risk_calculation_pb2.PARAMETRIC,
            confidence_level=risk_calculation_pb2.CL_95,
        )
        response = stub.DiscoverDependencies(request)

        for dep in response.dependencies:
            assert dep.data_type != market_data_dependencies_pb2.MARKET_DATA_TYPE_UNSPECIFIED
            assert dep.asset_class != ""
            assert dep.description != ""

    def test_deduplication_same_instrument(self, stub):
        # Two positions for the same equity instrument
        request = market_data_dependencies_pb2.DataDependenciesRequest(
            positions=[_equity_position("AAPL"), _equity_position("AAPL")],
            calculation_type=risk_calculation_pb2.PARAMETRIC,
            confidence_level=risk_calculation_pb2.CL_95,
        )
        response = stub.DiscoverDependencies(request)

        spot_prices = [
            d for d in response.dependencies
            if d.data_type == market_data_dependencies_pb2.SPOT_PRICE
        ]
        assert len(spot_prices) == 1

    def test_fixed_income_returns_yield_curve_and_credit_spread(self, stub):
        request = market_data_dependencies_pb2.DataDependenciesRequest(
            positions=[_fixed_income_position()],
            calculation_type=risk_calculation_pb2.PARAMETRIC,
            confidence_level=risk_calculation_pb2.CL_95,
        )
        response = stub.DiscoverDependencies(request)

        data_types = {d.data_type for d in response.dependencies}
        assert market_data_dependencies_pb2.YIELD_CURVE in data_types
        assert market_data_dependencies_pb2.CREDIT_SPREAD in data_types

    def test_required_flag_is_set(self, stub):
        request = market_data_dependencies_pb2.DataDependenciesRequest(
            positions=[_equity_position()],
            calculation_type=risk_calculation_pb2.PARAMETRIC,
            confidence_level=risk_calculation_pb2.CL_95,
        )
        response = stub.DiscoverDependencies(request)

        by_type = {d.data_type: d for d in response.dependencies}
        assert by_type[market_data_dependencies_pb2.SPOT_PRICE].required is True
        assert by_type[market_data_dependencies_pb2.HISTORICAL_PRICES].required is False

    def test_parameters_are_populated(self, stub):
        request = market_data_dependencies_pb2.DataDependenciesRequest(
            positions=[_equity_position()],
            calculation_type=risk_calculation_pb2.PARAMETRIC,
            confidence_level=risk_calculation_pb2.CL_95,
        )
        response = stub.DiscoverDependencies(request)

        hist = [
            d for d in response.dependencies
            if d.data_type == market_data_dependencies_pb2.HISTORICAL_PRICES
        ]
        assert len(hist) == 1
        assert hist[0].parameters["lookbackDays"] == "252"

    def test_full_portfolio_all_asset_classes(self, stub):
        request = market_data_dependencies_pb2.DataDependenciesRequest(
            positions=[
                _equity_position(),
                _fixed_income_position(),
                _commodity_position(),
                _derivative_position(),
                types_pb2.Position(
                    portfolio_id=types_pb2.PortfolioId(value="port-1"),
                    instrument_id=types_pb2.InstrumentId(value="EURUSD"),
                    asset_class=types_pb2.FX,
                    quantity=1000000.0,
                    market_value=types_pb2.Money(amount="1000000.00", currency="USD"),
                ),
            ],
            calculation_type=risk_calculation_pb2.MONTE_CARLO,
            confidence_level=risk_calculation_pb2.CL_99,
        )
        response = stub.DiscoverDependencies(request)

        data_types = {d.data_type for d in response.dependencies}
        assert market_data_dependencies_pb2.SPOT_PRICE in data_types
        assert market_data_dependencies_pb2.HISTORICAL_PRICES in data_types
        assert market_data_dependencies_pb2.YIELD_CURVE in data_types
        assert market_data_dependencies_pb2.CREDIT_SPREAD in data_types
        assert market_data_dependencies_pb2.FORWARD_CURVE in data_types
        assert market_data_dependencies_pb2.VOLATILITY_SURFACE in data_types
        assert market_data_dependencies_pb2.RISK_FREE_RATE in data_types
        assert market_data_dependencies_pb2.CORRELATION_MATRIX in data_types
        assert len(response.dependencies) > 0
