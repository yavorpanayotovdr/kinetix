"""Unit tests for the LiquidityAdjustedVaRServicer gRPC handler.

These tests exercise the servicer in isolation using a real in-process gRPC
server, verifying that the handler correctly translates proto messages to
domain calls and maps results back to the proto response.
"""
import math

import grpc
import pytest
from concurrent import futures

from kinetix.common import types_pb2
from kinetix.risk import liquidity_pb2, liquidity_pb2_grpc
from kinetix_risk.liquidity_server import LiquidityAdjustedVaRServicer


@pytest.fixture
def grpc_channel():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=2))
    liquidity_pb2_grpc.add_LiquidityRiskServiceServicer_to_server(
        LiquidityAdjustedVaRServicer(), server
    )
    port = server.add_insecure_port("[::]:0")
    server.start()
    channel = grpc.insecure_channel(f"localhost:{port}")
    yield channel
    server.stop(grace=None)
    channel.close()


@pytest.fixture
def stub(grpc_channel):
    return liquidity_pb2_grpc.LiquidityRiskServiceStub(grpc_channel)


def _make_request(
    book_id="BOOK-1",
    base_var=100_000.0,
    base_holding_period=1,
    inputs=None,
    stress_factors=None,
    portfolio_daily_vol=0.015,
):
    if inputs is None:
        inputs = [
            liquidity_pb2.LiquidityInput(
                instrument_id="AAPL",
                market_value=500_000.0,
                adv=10_000_000.0,
                adv_missing=False,
                adv_staleness_days=0,
                asset_class=types_pb2.EQUITY,
            ),
        ]
    return liquidity_pb2.LiquidityAdjustedVaRRequest(
        book_id=types_pb2.BookId(value=book_id),
        base_var=base_var,
        base_holding_period=base_holding_period,
        inputs=inputs,
        stress_factors=stress_factors or {},
        portfolio_daily_vol=portfolio_daily_vol,
    )


@pytest.mark.unit
class TestLiquidityAdjustedVaRServicer:

    def test_response_has_correct_book_id(self, stub):
        request = _make_request(book_id="BOOK-X")
        response = stub.CalculateLiquidityAdjustedVaR(request)
        assert response.book_id.value == "BOOK-X"

    def test_lvar_uses_sqrt_t_scaling_for_liquid_position(self, stub):
        """AAPL at 5% of ADV -> HIGH_LIQUID, horizon=1 -> lvar = var * sqrt(1/1)."""
        request = _make_request(base_var=100_000.0, base_holding_period=1)
        response = stub.CalculateLiquidityAdjustedVaR(request)

        # 500K / 10M = 5% -> HIGH_LIQUID, 1-day horizon -> lvar = base_var
        assert abs(response.portfolio_lvar - 100_000.0) < 1.0

    def test_lvar_is_amplified_for_illiquid_position(self, stub):
        """Position > 50% of ADV -> ILLIQUID, 10-day horizon -> lvar = var * sqrt(10)."""
        request = _make_request(
            base_var=100_000.0,
            base_holding_period=1,
            inputs=[
                liquidity_pb2.LiquidityInput(
                    instrument_id="ILLIQ",
                    market_value=6_000_000.0,
                    adv=10_000_000.0,
                    adv_missing=False,
                    adv_staleness_days=0,
                    asset_class=types_pb2.EQUITY,
                )
            ],
        )
        response = stub.CalculateLiquidityAdjustedVaR(request)
        expected_lvar = 100_000.0 * math.sqrt(10)
        assert abs(response.portfolio_lvar - expected_lvar) < 1.0

    def test_data_completeness_is_one_when_all_positions_have_adv(self, stub):
        request = _make_request()
        response = stub.CalculateLiquidityAdjustedVaR(request)
        assert abs(response.data_completeness - 1.0) < 1e-6

    def test_data_completeness_reflects_missing_adv(self, stub):
        """Half positions with missing ADV -> data_completeness = 0.5."""
        request = _make_request(
            inputs=[
                liquidity_pb2.LiquidityInput(
                    instrument_id="A",
                    market_value=500_000.0,
                    adv=10_000_000.0,
                    adv_missing=False,
                    adv_staleness_days=0,
                    asset_class=types_pb2.EQUITY,
                ),
                liquidity_pb2.LiquidityInput(
                    instrument_id="B",
                    market_value=500_000.0,
                    adv=0.0,
                    adv_missing=True,
                    adv_staleness_days=0,
                    asset_class=types_pb2.EQUITY,
                ),
            ]
        )
        response = stub.CalculateLiquidityAdjustedVaR(request)
        assert abs(response.data_completeness - 0.5) < 1e-6

    def test_position_with_no_adv_is_classified_as_illiquid(self, stub):
        request = _make_request(
            inputs=[
                liquidity_pb2.LiquidityInput(
                    instrument_id="NO-ADV",
                    market_value=1_000_000.0,
                    adv=0.0,
                    adv_missing=True,
                    adv_staleness_days=0,
                    asset_class=types_pb2.EQUITY,
                ),
            ]
        )
        response = stub.CalculateLiquidityAdjustedVaR(request)
        assert len(response.position_risks) == 1
        pos = response.position_risks[0]
        assert pos.tier == liquidity_pb2.ILLIQUID
        assert pos.horizon_days == 10
        assert pos.adv_missing is True

    def test_portfolio_concentration_status_is_worst_across_positions(self, stub):
        """When one position is BREACHED (no ADV), portfolio status should be BREACHED."""
        request = _make_request(
            inputs=[
                liquidity_pb2.LiquidityInput(
                    instrument_id="OK-POS",
                    market_value=500_000.0,
                    adv=10_000_000.0,
                    adv_missing=False,
                    adv_staleness_days=0,
                    asset_class=types_pb2.EQUITY,
                ),
                liquidity_pb2.LiquidityInput(
                    instrument_id="BAD-POS",
                    market_value=1_000_000.0,
                    adv=0.0,
                    adv_missing=True,
                    adv_staleness_days=0,
                    asset_class=types_pb2.EQUITY,
                ),
            ]
        )
        response = stub.CalculateLiquidityAdjustedVaR(request)
        assert response.portfolio_concentration_status == "BREACHED"

    def test_calculated_at_is_populated(self, stub):
        request = _make_request()
        response = stub.CalculateLiquidityAdjustedVaR(request)
        assert response.calculated_at.seconds > 0

    def test_invalid_base_holding_period_returns_error(self, stub):
        """base_holding_period of 0 should result in INVALID_ARGUMENT."""
        request = _make_request(base_holding_period=0)
        with pytest.raises(grpc.RpcError) as exc_info:
            stub.CalculateLiquidityAdjustedVaR(request)
        assert exc_info.value.code() == grpc.StatusCode.INVALID_ARGUMENT
