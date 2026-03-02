import logging

import grpc

from kinetix.risk import market_data_dependencies_pb2_grpc
from kinetix_risk.converters import dependencies_to_proto, proto_positions_to_domain
from kinetix_risk.dependencies import discover

logger = logging.getLogger(__name__)


class MarketDataDependenciesServicer(
    market_data_dependencies_pb2_grpc.MarketDataDependenciesServiceServicer,
):

    def DiscoverDependencies(self, request, context):
        try:
            positions = proto_positions_to_domain(request.positions)
            dependencies = discover(positions)
            return dependencies_to_proto(dependencies)
        except ValueError as e:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(e))
        except Exception as e:
            logger.exception("DiscoverDependencies failed")
            context.abort(grpc.StatusCode.INTERNAL, str(e))
