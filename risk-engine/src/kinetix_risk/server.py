import logging
import os
import signal
from concurrent import futures
from pathlib import Path

import grpc
import prometheus_client

logger = logging.getLogger(__name__)

from kinetix.risk import liquidity_pb2_grpc, market_data_dependencies_pb2_grpc, ml_prediction_pb2_grpc, regulatory_reporting_pb2_grpc, risk_calculation_pb2_grpc, stress_testing_pb2_grpc
from kinetix_risk.converters import (
    cross_book_var_result_to_proto_response,
    proto_calculation_type_to_domain,
    proto_confidence_to_domain,
    proto_market_data_to_domain,
    proto_positions_to_domain,
    proto_valuation_outputs_to_names,
    valuation_result_to_proto_response,
    var_result_to_proto_response,
)
from kinetix_risk.version import get_model_version
from kinetix_risk.market_data_consumer import consume_market_data
from kinetix_risk.metrics import risk_var_component_contribution, risk_var_expected_shortfall, risk_var_value
from kinetix_risk.ml.model_store import ModelStore
from kinetix_risk.ml_server import MLPredictionServicer
from kinetix_risk.cross_book_var import calculate_cross_book_var
from kinetix_risk.portfolio_risk import calculate_book_var
from kinetix_risk.valuation import calculate_valuation
from kinetix_risk.volatility import VolatilityProvider
from kinetix_risk.dependencies_server import MarketDataDependenciesServicer
from kinetix_risk.liquidity_server import LiquidityAdjustedVaRServicer
from kinetix_risk.regulatory_server import RegulatoryReportingServicer
from kinetix_risk.stress_server import StressTestServicer


class RiskCalculationServicer(risk_calculation_pb2_grpc.RiskCalculationServiceServicer):

    def CalculateVaR(self, request, context):
        try:
            positions = proto_positions_to_domain(request.positions)
            calc_type = proto_calculation_type_to_domain(request.calculation_type)
            confidence = proto_confidence_to_domain(request.confidence_level)

            market_data_dicts = proto_market_data_to_domain(request.market_data)
            bundle = consume_market_data(market_data_dicts)

            result = calculate_book_var(
                positions=positions,
                calculation_type=calc_type,
                confidence_level=confidence,
                time_horizon_days=request.time_horizon_days or 1,
                num_simulations=request.num_simulations or 10_000,
                volatility_provider=bundle.volatility_provider or VolatilityProvider.static(),
                correlation_matrix=bundle.correlation_matrix,
            )

            book_id = request.book_id.value
            risk_var_value.labels(book_id=book_id).set(result.var_value)
            risk_var_expected_shortfall.labels(book_id=book_id).set(result.expected_shortfall)
            for component in result.component_breakdown:
                risk_var_component_contribution.labels(
                    book_id=book_id,
                    asset_class=component.asset_class.value,
                ).set(component.var_contribution)

            return var_result_to_proto_response(
                result,
                book_id=request.book_id.value,
                calculation_type=request.calculation_type,
                confidence_level=request.confidence_level,
            )
        except ValueError as e:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(e))
        except Exception as e:
            logger.exception("CalculateVaR failed")
            context.abort(grpc.StatusCode.INTERNAL, str(e))

    def Valuate(self, request, context):
        try:
            positions = proto_positions_to_domain(request.positions)
            calc_type = proto_calculation_type_to_domain(request.calculation_type)
            confidence = proto_confidence_to_domain(request.confidence_level)

            market_data_dicts = proto_market_data_to_domain(request.market_data)
            bundle = consume_market_data(market_data_dicts)

            requested_outputs = proto_valuation_outputs_to_names(request.requested_outputs)

            # A seed of 0 means unseeded (non-deterministic); >0 means deterministic
            seed = request.monte_carlo_seed if request.monte_carlo_seed > 0 else None

            result = calculate_valuation(
                positions=positions,
                calculation_type=calc_type,
                confidence_level=confidence,
                time_horizon_days=request.time_horizon_days or 1,
                num_simulations=request.num_simulations or 10_000,
                volatility_provider=bundle.volatility_provider or VolatilityProvider.static(),
                correlation_matrix=bundle.correlation_matrix,
                requested_outputs=requested_outputs,
                book_id=request.book_id.value,
                seed=seed,
                market_data_bundle=bundle,
            )

            if result.var_result is not None:
                book_id = request.book_id.value
                risk_var_value.labels(book_id=book_id).set(result.var_result.var_value)
                risk_var_expected_shortfall.labels(book_id=book_id).set(result.var_result.expected_shortfall)
                for component in result.var_result.component_breakdown:
                    risk_var_component_contribution.labels(
                        book_id=book_id,
                        asset_class=component.asset_class.value,
                    ).set(component.var_contribution)

            return valuation_result_to_proto_response(
                result,
                book_id=request.book_id.value,
                calculation_type=request.calculation_type,
                confidence_level=request.confidence_level,
                model_version=get_model_version(),
                monte_carlo_seed=request.monte_carlo_seed,
            )
        except ValueError as e:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(e))
        except Exception as e:
            logger.exception("Valuate failed")
            context.abort(grpc.StatusCode.INTERNAL, str(e))

    def CalculateCrossBookVaR(self, request, context):
        try:
            positions = proto_positions_to_domain(request.positions)
            calc_type = proto_calculation_type_to_domain(request.calculation_type)
            confidence = proto_confidence_to_domain(request.confidence_level)

            market_data_dicts = proto_market_data_to_domain(request.market_data)
            bundle = consume_market_data(market_data_dicts)

            # Group positions by book_id
            book_ids = list(request.book_ids)
            books: dict[str, list] = {bid: [] for bid in book_ids}
            for pos, proto_pos in zip(positions, request.positions):
                bid = proto_pos.book_id if proto_pos.book_id else ""
                if bid in books:
                    books[bid].append(pos)
                else:
                    books.setdefault(bid, []).append(pos)

            seed = request.monte_carlo_seed if request.monte_carlo_seed > 0 else None

            result = calculate_cross_book_var(
                books=books,
                calculation_type=calc_type,
                confidence_level=confidence,
                time_horizon_days=request.time_horizon_days or 1,
                num_simulations=request.num_simulations or 10_000,
                volatility_provider=bundle.volatility_provider or VolatilityProvider.static(),
                correlation_matrix=bundle.correlation_matrix,
                seed=seed,
            )

            return cross_book_var_result_to_proto_response(
                result,
                book_ids=book_ids,
                portfolio_group_id=request.portfolio_group_id,
                calculation_type=request.calculation_type,
                confidence_level=request.confidence_level,
                model_version=get_model_version(),
                monte_carlo_seed=request.monte_carlo_seed,
            )
        except ValueError as e:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(e))
        except Exception as e:
            logger.exception("CalculateCrossBookVaR failed")
            context.abort(grpc.StatusCode.INTERNAL, str(e))

    def CalculateVaRStream(self, request_iterator, context):
        for request in request_iterator:
            try:
                yield self.CalculateVaR(request, context)
            except Exception as e:
                logger.exception("CalculateVaRStream failed for request")
                context.abort(grpc.StatusCode.INTERNAL, str(e))


def serve(port: int = 50051, metrics_port: int = 9091, models_dir: str = "models"):
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(threadName)s] %(levelname)-5s %(name)s - %(message)s",
    )

    # Configure OTel logging if endpoint is set
    otel_endpoint = os.environ.get("OTEL_EXPORTER_OTLP_ENDPOINT")
    if otel_endpoint:
        try:
            from opentelemetry.sdk._logs import LoggerProvider
            from opentelemetry.sdk._logs.export import BatchLogRecordProcessor
            from opentelemetry.exporter.otlp.proto.grpc._log_exporter import OTLPLogExporter
            from opentelemetry.sdk.resources import Resource

            resource = Resource.create({"service.name": os.environ.get("OTEL_SERVICE_NAME", "risk-engine")})
            provider = LoggerProvider(resource=resource)
            provider.add_log_record_processor(BatchLogRecordProcessor(OTLPLogExporter(endpoint=otel_endpoint, insecure=True)))

            from opentelemetry._logs import set_logger_provider
            set_logger_provider(provider)

            from opentelemetry.sdk._logs.export import LoggingHandler
            handler = LoggingHandler(level=logging.INFO, logger_provider=provider)
            logging.getLogger().addHandler(handler)
            logger.info("OTel logging configured, exporting to %s", otel_endpoint)
        except Exception:
            logger.warning("Failed to configure OTel logging, continuing without it", exc_info=True)

    prometheus_client.start_http_server(metrics_port)
    logger.info("Prometheus metrics server started on port %d", metrics_port)

    max_message_size = 50 * 1024 * 1024  # 50 MB
    server = grpc.server(
        futures.ThreadPoolExecutor(max_workers=10),
        options=[
            ("grpc.max_send_message_length", max_message_size),
            ("grpc.max_receive_message_length", max_message_size),
        ],
    )
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
    liquidity_pb2_grpc.add_LiquidityRiskServiceServicer_to_server(
        LiquidityAdjustedVaRServicer(), server
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
        logger.info("Risk engine gRPC server started on port %d with TLS", port)
    else:
        server.add_insecure_port(f"[::]:{port}")
        logger.info("Risk engine gRPC server started on port %d (plaintext)", port)

    server.start()

    def handle_sigterm(signum, frame):
        logger.info("SIGTERM received, initiating graceful shutdown")
        stopped = server.stop(grace=30)
        stopped.wait()
        logger.info("gRPC server stopped cleanly")

    signal.signal(signal.SIGTERM, handle_sigterm)

    server.wait_for_termination()


if __name__ == "__main__":
    serve()
