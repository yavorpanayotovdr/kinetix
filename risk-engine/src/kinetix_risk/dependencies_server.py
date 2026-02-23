from kinetix.risk import market_data_dependencies_pb2_grpc
from kinetix_risk.converters import dependencies_to_proto, proto_positions_to_domain
from kinetix_risk.dependencies import discover


class MarketDataDependenciesServicer(
    market_data_dependencies_pb2_grpc.MarketDataDependenciesServiceServicer,
):

    def DiscoverDependencies(self, request, context):
        positions = proto_positions_to_domain(request.positions)
        dependencies = discover(positions)
        return dependencies_to_proto(dependencies)
