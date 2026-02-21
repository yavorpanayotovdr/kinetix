import time

from kinetix.risk import regulatory_reporting_pb2, regulatory_reporting_pb2_grpc
from kinetix_risk.converters import (
    frtb_result_to_proto,
    proto_positions_to_domain,
    report_to_proto,
)
from kinetix_risk.frtb.calculator import calculate_frtb
from kinetix_risk.frtb.report import generate_csv_report, generate_xbrl_report
from kinetix_risk.metrics import (
    frtb_calculation_duration_seconds,
    frtb_calculation_total,
    regulatory_report_total,
)


class RegulatoryReportingServicer(
    regulatory_reporting_pb2_grpc.RegulatoryReportingServiceServicer,
):

    def CalculateFrtb(self, request, context):
        start = time.time()
        try:
            positions = proto_positions_to_domain(request.positions)
            portfolio_id = request.portfolio_id.value

            result = calculate_frtb(positions, portfolio_id)

            frtb_calculation_total.inc()
            return frtb_result_to_proto(result)
        finally:
            duration = time.time() - start
            frtb_calculation_duration_seconds.observe(duration)

    def GenerateReport(self, request, context):
        positions = proto_positions_to_domain(request.positions)
        portfolio_id = request.portfolio_id.value

        result = calculate_frtb(positions, portfolio_id)

        fmt = request.format
        if fmt == regulatory_reporting_pb2.XBRL:
            content = generate_xbrl_report(result)
            fmt_label = "XBRL"
        else:
            content = generate_csv_report(result)
            fmt_label = "CSV"

        regulatory_report_total.labels(format=fmt_label).inc()
        return report_to_proto(portfolio_id, fmt, content)
