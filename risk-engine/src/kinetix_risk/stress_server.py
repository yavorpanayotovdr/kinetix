import time

import grpc

from kinetix.risk import stress_testing_pb2, stress_testing_pb2_grpc
from kinetix_risk.converters import (
    greeks_result_to_proto,
    proto_calculation_type_to_domain,
    proto_confidence_to_domain,
    proto_positions_to_domain,
    proto_stress_request_to_scenario,
    stress_result_to_proto,
)
from kinetix_risk.greeks import calculate_greeks
from kinetix_risk.metrics import (
    greeks_calculation_duration_seconds,
    greeks_calculation_total,
    stress_test_duration_seconds,
    stress_test_total,
)
from kinetix_risk.stress.engine import run_stress_test
from kinetix_risk.stress.scenarios import get_scenario, list_scenarios


class StressTestServicer(stress_testing_pb2_grpc.StressTestServiceServicer):

    def RunStressTest(self, request, context):
        scenario_name = request.scenario_name
        start = time.time()
        try:
            # Try historical scenario first, fall back to hypothetical
            if scenario_name and not request.vol_shocks and not request.price_shocks:
                try:
                    scenario = get_scenario(scenario_name)
                except KeyError:
                    context.set_code(grpc.StatusCode.NOT_FOUND)
                    context.set_details(f"Unknown scenario: {scenario_name}")
                    return stress_testing_pb2.StressTestResponse()
            else:
                scenario = proto_stress_request_to_scenario(request)

            positions = proto_positions_to_domain(request.positions)
            calc_type = proto_calculation_type_to_domain(request.calculation_type)
            confidence = proto_confidence_to_domain(request.confidence_level)

            result = run_stress_test(
                positions=positions,
                scenario=scenario,
                calculation_type=calc_type,
                confidence_level=confidence,
                time_horizon_days=request.time_horizon_days or 1,
            )

            stress_test_total.labels(scenario_name=result.scenario_name).inc()
            return stress_result_to_proto(result)
        finally:
            duration = time.time() - start
            stress_test_duration_seconds.labels(scenario_name=scenario_name).observe(duration)

    def ListScenarios(self, request, context):
        names = list_scenarios()
        return stress_testing_pb2.ListScenariosResponse(
            scenario_names=names,
        )

    def CalculateGreeks(self, request, context):
        start = time.time()
        try:
            positions = proto_positions_to_domain(request.positions)
            calc_type = proto_calculation_type_to_domain(request.calculation_type)
            confidence = proto_confidence_to_domain(request.confidence_level)

            result = calculate_greeks(
                positions=positions,
                calculation_type=calc_type,
                confidence_level=confidence,
                time_horizon_days=request.time_horizon_days or 1,
                portfolio_id=request.portfolio_id.value,
            )

            greeks_calculation_total.inc()
            return greeks_result_to_proto(result)
        finally:
            duration = time.time() - start
            greeks_calculation_duration_seconds.observe(duration)
