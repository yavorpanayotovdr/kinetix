import os
from concurrent import futures
from pathlib import Path

import grpc
import prometheus_client

from kinetix.risk import market_data_dependencies_pb2_grpc, ml_prediction_pb2_grpc, regulatory_reporting_pb2_grpc, risk_calculation_pb2_grpc, stress_testing_pb2_grpc
from kinetix_risk.converters import (
    proto_calculation_type_to_domain,
    proto_confidence_to_domain,
    proto_market_data_to_domain,
    proto_positions_to_domain,
    proto_valuation_outputs_to_names,
    valuation_result_to_proto_response,
    var_result_to_proto_response,
)
from kinetix_risk.market_data_consumer import consume_market_data
from kinetix_risk.metrics import risk_var_component_contribution, risk_var_expected_shortfall, risk_var_value
from kinetix_risk.ml.model_store import ModelStore
from kinetix_risk.ml_server import MLPredictionServicer
from kinetix_risk.portfolio_risk import calculate_portfolio_var
from kinetix_risk.valuation import calculate_valuation
from kinetix_risk.volatility import VolatilityProvider
from kinetix_risk.dependencies_server import MarketDataDependenciesServicer
from kinetix_risk.regulatory_server import RegulatoryReportingServicer
from kinetix_risk.stress_server import StressTestServicer


class RiskCalculationServicer(risk_calculation_pb2_grpc.RiskCalculationServiceServicer):

    def CalculateVaR(self, request, context):
        positions = proto_positions_to_domain(request.positions)
        calc_type = proto_calculation_type_to_domain(request.calculation_type)
        confidence = proto_confidence_to_domain(request.confidence_level)

        market_data_dicts = proto_market_data_to_domain(request.market_data)
        bundle = consume_market_data(market_data_dicts)

        result = calculate_portfolio_var(
            positions=positions,
            calculation_type=calc_type,
            confidence_level=confidence,
            time_horizon_days=request.time_horizon_days or 1,
            num_simulations=request.num_simulations or 10_000,
            volatility_provider=bundle.volatility_provider or VolatilityProvider.static(),
            correlation_matrix=bundle.correlation_matrix,
        )

        portfolio_id = request.portfolio_id.value
        risk_var_value.labels(portfolio_id=portfolio_id).set(result.var_value)
        risk_var_expected_shortfall.labels(portfolio_id=portfolio_id).set(result.expected_shortfall)
        for component in result.component_breakdown:
            risk_var_component_contribution.labels(
                portfolio_id=portfolio_id,
                asset_class=component.asset_class.value,
            ).set(component.var_contribution)

        return var_result_to_proto_response(
            result,
            portfolio_id=request.portfolio_id.value,
            calculation_type=request.calculation_type,
            confidence_level=request.confidence_level,
        )

    def Valuate(self, request, context):
        positions = proto_positions_to_domain(request.positions)
        calc_type = proto_calculation_type_to_domain(request.calculation_type)
        confidence = proto_confidence_to_domain(request.confidence_level)

        market_data_dicts = proto_market_data_to_domain(request.market_data)
        bundle = consume_market_data(market_data_dicts)

        requested_outputs = proto_valuation_outputs_to_names(request.requested_outputs)

        result = calculate_valuation(
            positions=positions,
            calculation_type=calc_type,
            confidence_level=confidence,
            time_horizon_days=request.time_horizon_days or 1,
            num_simulations=request.num_simulations or 10_000,
            volatility_provider=bundle.volatility_provider or VolatilityProvider.static(),
            correlation_matrix=bundle.correlation_matrix,
            requested_outputs=requested_outputs,
            portfolio_id=request.portfolio_id.value,
        )

        if result.var_result is not None:
            portfolio_id = request.portfolio_id.value
            risk_var_value.labels(portfolio_id=portfolio_id).set(result.var_result.var_value)
            risk_var_expected_shortfall.labels(portfolio_id=portfolio_id).set(result.var_result.expected_shortfall)
            for component in result.var_result.component_breakdown:
                risk_var_component_contribution.labels(
                    portfolio_id=portfolio_id,
                    asset_class=component.asset_class.value,
                ).set(component.var_contribution)

        return valuation_result_to_proto_response(
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
    market_data_dependencies_pb2_grpc.add_MarketDataDependenciesServiceServicer_to_server(
        MarketDataDependenciesServicer(), server
    )

    tls_enabled = os.environ.get("GRPC_TLS_ENABLED", "false").lower() == "true"

    if tls_enabled:
        cert_path = os.environ.get("GRPC_TLS_CERT", "certs/server-cert.pem")
        key_path = os.environ.get("GRPC_TLS_KEY", "certs/server-key.pem")
        ca_path = os.environ.get("GRPC_TLS_CA", "certs/ca-cert.pem")

        with open(key_path, "rb") as f:
            private_key = f.read()
        with open(cert_path, "rb") as f:
            certificate_chain = f.read()
        with open(ca_path, "rb") as f:
            root_certificates = f.read()

        credentials = grpc.ssl_server_credentials(
            [(private_key, certificate_chain)],
            root_certificates=root_certificates,
            require_client_auth=False,
        )
        server.add_secure_port(f"[::]:{port}", credentials)
        print(f"Risk engine gRPC server started on port {port} with TLS")
    else:
        server.add_insecure_port(f"[::]:{port}")
        print(f"Risk engine gRPC server started on port {port} (plaintext)")

    server.start()
    server.wait_for_termination()


if __name__ == "__main__":
    serve()
