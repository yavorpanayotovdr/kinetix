import time

import grpc
import pytest
from concurrent import futures

pytestmark = pytest.mark.performance

from kinetix.common import types_pb2
from kinetix.risk import risk_calculation_pb2, risk_calculation_pb2_grpc
from kinetix_risk.models import (
    AssetClass, CalculationType, ConfidenceLevel, PositionRisk,
)
from kinetix_risk.portfolio_risk import calculate_portfolio_var
from kinetix_risk.server import RiskCalculationServicer
from kinetix_risk.var_monte_carlo import calculate_monte_carlo_var
from kinetix_risk.volatility import get_sub_correlation_matrix, get_volatility

ALL_ASSET_CLASSES = list(AssetClass)

PROTO_ASSET_CLASSES = [
    types_pb2.EQUITY,
    types_pb2.FIXED_INCOME,
    types_pb2.FX,
    types_pb2.COMMODITY,
    types_pb2.DERIVATIVE,
]


def make_positions(n: int = 10_000) -> list[PositionRisk]:
    return [
        PositionRisk(
            instrument_id=f"INST-{i}",
            asset_class=ALL_ASSET_CLASSES[i % len(ALL_ASSET_CLASSES)],
            market_value=50_000.0 + (i % 100) * 1_000.0,
            currency="USD",
        )
        for i in range(n)
    ]


def make_proto_positions(n: int = 10_000) -> list[types_pb2.Position]:
    return [
        types_pb2.Position(
            portfolio_id=types_pb2.PortfolioId(value="perf-port"),
            instrument_id=types_pb2.InstrumentId(value=f"INST-{i}"),
            asset_class=PROTO_ASSET_CLASSES[i % len(PROTO_ASSET_CLASSES)],
            quantity=100.0,
            market_value=types_pb2.Money(
                amount=f"{50_000.0 + (i % 100) * 1_000.0:.2f}",
                currency="USD",
            ),
        )
        for i in range(n)
    ]


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


class TestMonteCarloPerformance:
    def test_10k_positions_10k_sims_under_60s(self):
        positions = make_positions(10_000)
        start = time.time()
        result = calculate_portfolio_var(
            positions, CalculationType.MONTE_CARLO, ConfidenceLevel.CL_95,
            time_horizon_days=1, num_simulations=10_000,
        )
        duration = time.time() - start

        assert duration < 60, f"Took {duration:.2f}s, exceeds 60s SLA"
        assert result.var_value > 0
        assert result.expected_shortfall > result.var_value
        assert len(result.component_breakdown) == 5

    def test_10k_positions_10k_sims_via_grpc_under_60s(self, stub):
        positions = make_proto_positions(10_000)
        request = risk_calculation_pb2.VaRRequest(
            portfolio_id=types_pb2.PortfolioId(value="perf-port"),
            calculation_type=risk_calculation_pb2.MONTE_CARLO,
            confidence_level=risk_calculation_pb2.CL_95,
            time_horizon_days=1,
            num_simulations=10_000,
            positions=positions,
        )

        start = time.time()
        response = stub.CalculateVaR(request)
        duration = time.time() - start

        assert duration < 60, f"Took {duration:.2f}s, exceeds 60s SLA"
        assert response.var_value > 0
        assert len(response.component_breakdown) == 5

    def test_result_correctness_at_scale(self):
        positions = make_positions(10_000)

        # Aggregate positions the same way portfolio_risk does
        from collections import defaultdict
        grouped: dict[AssetClass, float] = defaultdict(float)
        for pos in positions:
            grouped[pos.asset_class] += pos.market_value
        asset_classes = sorted(grouped.keys(), key=lambda ac: ac.value)
        from kinetix_risk.models import AssetClassExposure
        exposures = [
            AssetClassExposure(ac, grouped[ac], get_volatility(ac))
            for ac in asset_classes
        ]
        corr = get_sub_correlation_matrix(asset_classes)

        result1 = calculate_monte_carlo_var(
            exposures, ConfidenceLevel.CL_95, 1, corr,
            num_simulations=10_000, seed=42,
        )
        result2 = calculate_monte_carlo_var(
            exposures, ConfidenceLevel.CL_95, 1, corr,
            num_simulations=10_000, seed=42,
        )

        # Deterministic with same seed
        assert result1.var_value == result2.var_value
        assert result1.expected_shortfall == result2.expected_shortfall

        # All 5 asset classes in breakdown
        breakdown_classes = {cb.asset_class for cb in result1.component_breakdown}
        assert breakdown_classes == set(AssetClass)

        # Percentages sum to ~100%
        total_pct = sum(cb.percentage_of_total for cb in result1.component_breakdown)
        assert total_pct == pytest.approx(100.0, abs=1.0)
