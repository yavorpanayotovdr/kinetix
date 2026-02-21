import csv
import io
import xml.etree.ElementTree as ET

from kinetix_risk.models import FrtbResult


def generate_csv_report(result: FrtbResult) -> str:
    output = io.StringIO()
    writer = csv.writer(output)

    writer.writerow([
        "Component", "Risk Class", "Delta Charge", "Vega Charge",
        "Curvature Charge", "Total Charge",
    ])

    for rcc in result.sbm.risk_class_charges:
        writer.writerow([
            "SbM",
            rcc.risk_class.value,
            f"{rcc.delta_charge:.2f}",
            f"{rcc.vega_charge:.2f}",
            f"{rcc.curvature_charge:.2f}",
            f"{rcc.total_charge:.2f}",
        ])

    writer.writerow([
        "DRC", "", f"{result.drc.gross_jtd:.2f}",
        f"{result.drc.hedge_benefit:.2f}", "",
        f"{result.drc.net_drc:.2f}",
    ])

    writer.writerow([
        "RRAO", "", f"{result.rrao.exotic_notional:.2f}",
        f"{result.rrao.other_notional:.2f}", "",
        f"{result.rrao.total_rrao:.2f}",
    ])

    writer.writerow([
        "Total", "", "", "", "", f"{result.total_capital_charge:.2f}",
    ])

    return output.getvalue()


def generate_xbrl_report(result: FrtbResult) -> str:
    root = ET.Element("FRTBReport")
    root.set("portfolioId", result.portfolio_id)

    sbm_elem = ET.SubElement(root, "SensitivitiesBasedMethod")
    for rcc in result.sbm.risk_class_charges:
        rc_elem = ET.SubElement(sbm_elem, "RiskClassCharge")
        rc_elem.set("riskClass", rcc.risk_class.value)
        ET.SubElement(rc_elem, "DeltaCharge").text = f"{rcc.delta_charge:.2f}"
        ET.SubElement(rc_elem, "VegaCharge").text = f"{rcc.vega_charge:.2f}"
        ET.SubElement(rc_elem, "CurvatureCharge").text = f"{rcc.curvature_charge:.2f}"
        ET.SubElement(rc_elem, "TotalCharge").text = f"{rcc.total_charge:.2f}"
    ET.SubElement(sbm_elem, "TotalSbmCharge").text = f"{result.sbm.total_sbm_charge:.2f}"

    drc_elem = ET.SubElement(root, "DefaultRiskCharge")
    ET.SubElement(drc_elem, "GrossJTD").text = f"{result.drc.gross_jtd:.2f}"
    ET.SubElement(drc_elem, "HedgeBenefit").text = f"{result.drc.hedge_benefit:.2f}"
    ET.SubElement(drc_elem, "NetDRC").text = f"{result.drc.net_drc:.2f}"

    rrao_elem = ET.SubElement(root, "ResidualRiskAddOn")
    ET.SubElement(rrao_elem, "ExoticNotional").text = f"{result.rrao.exotic_notional:.2f}"
    ET.SubElement(rrao_elem, "OtherNotional").text = f"{result.rrao.other_notional:.2f}"
    ET.SubElement(rrao_elem, "TotalRRAO").text = f"{result.rrao.total_rrao:.2f}"

    ET.SubElement(root, "TotalCapitalCharge").text = f"{result.total_capital_charge:.2f}"

    return ET.tostring(root, encoding="unicode", xml_declaration=True)
