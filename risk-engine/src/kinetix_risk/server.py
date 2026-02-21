from concurrent import futures
from pathlib import Path

import grpc
import prometheus_client

from kinetix.risk import ml_prediction_pb2_grpc, regulatory_reporting_pb2_grpc, risk_calculation_pb2_grpc, stress_testing_pb2_grpc
from kinetix_risk.converters import (
    proto_calculation_type_to_domain,
    proto_confidence_to_domain,
    proto_positions_to_domain,
    var_result_to_proto_response,
)
from kinetix_risk.metrics import risk_var_value
from kinetix_risk.ml.model_store import ModelStore
from kinetix_risk.ml_server import MLPredictionServicer
from kinetix_risk.portfolio_risk import calculate_portfolio_var
from kinetix_risk.regulatory_server import RegulatoryReportingServicer
from kinetix_risk.stress_server import StressTestServicer


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

        risk_var_value.labels(portfolio_id=request.portfolio_id.value).set(result.var_value)

        return var_result_to_proto_response(
            result,
            portfolio_id=request.portfolio_id.value,
            calculation_type=request.calculation_type,
            confidence_level=request.confidence_level,
        )

    def CalculateVaRStream(self, request_iterator, context):
        for request in request_iterator:
            yield self.CalculateVaR(request, context)


def serve(port: int = 50051, metrics_port: int = 9091, models_dir: str = "models"):
    prometheus_client.start_http_server(metrics_port)
    print(f"Prometheus metrics server started on port {metrics_port}")

    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    risk_calculation_pb2_grpc.add_RiskCalculationServiceServicer_to_server(
        RiskCalculationServicer(), server
    )
    model_store = ModelStore(Path(models_dir))
    ml_prediction_pb2_grpc.add_MLPredictionServiceServicer_to_server(
        MLPredictionServicer(model_store), server
    )
    stress_testing_pb2_grpc.add_StressTestServiceServicer_to_server(
        StressTestServicer(), server
    )
    regulatory_reporting_pb2_grpc.add_RegulatoryReportingServiceServicer_to_server(
        RegulatoryReportingServicer(), server
    )
    server.add_insecure_port(f"[::]:{port}")
    server.start()
    print(f"Risk engine gRPC server started on port {port}")
    server.wait_for_termination()


if __name__ == "__main__":
    serve()
