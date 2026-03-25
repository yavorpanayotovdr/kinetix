"""gRPC servicer for Brinson-Hood-Beebower performance attribution."""

import logging

import grpc

from kinetix.risk import attribution_pb2, attribution_pb2_grpc
from kinetix_risk.brinson import BrinsonResult, brinson_multi_period, brinson_single_period

logger = logging.getLogger(__name__)


class AttributionServicer(attribution_pb2_grpc.AttributionServiceServicer):
    """Handles CalculateBrinsonAttribution RPC calls."""

    def CalculateBrinsonAttribution(self, request, context):
        if not request.periods:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "periods must not be empty")
            return

        try:
            period_kwargs = []
            for period in request.periods:
                if not period.sectors:
                    context.abort(grpc.StatusCode.INVALID_ARGUMENT, "each period must have at least one sector")
                    return
                period_kwargs.append(
                    dict(
                        sector_labels=[s.sector_label for s in period.sectors],
                        portfolio_weights=[s.portfolio_weight for s in period.sectors],
                        benchmark_weights=[s.benchmark_weight for s in period.sectors],
                        portfolio_returns=[s.portfolio_return for s in period.sectors],
                        benchmark_returns=[s.benchmark_return for s in period.sectors],
                        total_benchmark_return=period.total_benchmark_return,
                    )
                )

            if len(period_kwargs) == 1:
                result: BrinsonResult = brinson_single_period(**period_kwargs[0])
            else:
                result = brinson_multi_period(period_kwargs)

        except ValueError as e:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(e))
            return
        except Exception as e:
            logger.exception("Unexpected error in CalculateBrinsonAttribution")
            context.abort(grpc.StatusCode.INTERNAL, f"Internal error: {e}")
            return

        sector_results = [
            attribution_pb2.SectorAttributionResult(
                sector_label=s.sector_label,
                portfolio_weight=s.portfolio_weight,
                benchmark_weight=s.benchmark_weight,
                portfolio_return=s.portfolio_return,
                benchmark_return=s.benchmark_return,
                allocation_effect=s.allocation_effect,
                selection_effect=s.selection_effect,
                interaction_effect=s.interaction_effect,
                total_active_contribution=s.total_active_contribution,
            )
            for s in result.sectors
        ]

        return attribution_pb2.BrinsonAttributionResponse(
            sectors=sector_results,
            total_active_return=result.total_active_return,
            total_allocation_effect=result.total_allocation_effect,
            total_selection_effect=result.total_selection_effect,
            total_interaction_effect=result.total_interaction_effect,
        )
