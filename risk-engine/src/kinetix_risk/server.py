from concurrent import futures

import grpc

from kinetix.risk import risk_calculation_pb2_grpc
from kinetix_risk.converters import (
    proto_calculation_type_to_domain,
    proto_confidence_to_domain,
    proto_positions_to_domain,
    var_result_to_proto_response,
)
from kinetix_risk.portfolio_risk import calculate_portfolio_var


class RiskCalculationServicer(risk_calculation_pb2_grpc.RiskCalculationServiceServicer):

    def CalculateVaR(self, request, context):
        positions = proto_positions_to_domain(request.positions)
        calc_type = proto_calculation_type_to_domain(request.calculation_type)
        confidence = proto_confidence_to_domain(request.confidence_level)

        result = calculate_portfolio_var(
            positions=positions,
            calculation_type=calc_type,
            confidence_level=confidence,
            time_horizon_days=request.time_horizon_days or 1,
            num_simulations=request.num_simulations or 10_000,
        )

        return var_result_to_proto_response(
            result,
            portfolio_id=request.portfolio_id.value,
            calculation_type=request.calculation_type,
            confidence_level=request.confidence_level,
        )

    def CalculateVaRStream(self, request_iterator, context):
        for request in request_iterator:
            yield self.CalculateVaR(request, context)


def serve(port: int = 50051):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    risk_calculation_pb2_grpc.add_RiskCalculationServiceServicer_to_server(
        RiskCalculationServicer(), server
    )
    server.add_insecure_port(f"[::]:{port}")
    server.start()
    print(f"Risk engine gRPC server started on port {port}")
    server.wait_for_termination()


if __name__ == "__main__":
    serve()
