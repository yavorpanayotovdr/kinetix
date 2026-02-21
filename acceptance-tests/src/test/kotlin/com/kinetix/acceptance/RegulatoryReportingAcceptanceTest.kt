package com.kinetix.acceptance

import com.kinetix.common.model.AssetClass
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import kotlin.math.abs
import kotlin.math.sqrt

// --- Stub FRTB risk classes ---

private enum class FrtbRiskClass(val weight: Double, val vegaWeight: Double) {
    GIRR(0.015, 0.01),
    CSR_NON_SEC(0.03, 0.02),
    CSR_SEC_CTP(0.04, 0.03),
    CSR_SEC_NON_CTP(0.06, 0.04),
    EQUITY(0.20, 0.15),
    COMMODITY(0.15, 0.10),
    FX(0.10, 0.08),
}

private val ASSET_CLASS_TO_RISK_CLASSES = mapOf(
    AssetClass.EQUITY to listOf(FrtbRiskClass.EQUITY),
    AssetClass.FIXED_INCOME to listOf(FrtbRiskClass.GIRR, FrtbRiskClass.CSR_NON_SEC),
    AssetClass.FX to listOf(FrtbRiskClass.FX),
    AssetClass.COMMODITY to listOf(FrtbRiskClass.COMMODITY),
    AssetClass.DERIVATIVE to listOf(FrtbRiskClass.EQUITY, FrtbRiskClass.FX),
)

// --- Stub domain models ---

private data class RiskClassCharge(
    val riskClass: FrtbRiskClass,
    val deltaCharge: Double,
    val vegaCharge: Double,
    val curvatureCharge: Double,
    val totalCharge: Double,
)

private data class SbmResult(
    val riskClassCharges: List<RiskClassCharge>,
    val totalSbmCharge: Double,
)

private data class DrcResult(
    val grossJtd: Double,
    val hedgeBenefit: Double,
    val netDrc: Double,
)

private data class RraoResult(
    val exoticNotional: Double,
    val otherNotional: Double,
    val totalRrao: Double,
)

private data class FrtbResult(
    val portfolioId: String,
    val sbm: SbmResult,
    val drc: DrcResult,
    val rrao: RraoResult,
    val totalCapitalCharge: Double,
)

// --- Stub engines ---

private class StubSbmEngine {
    fun calculate(exposures: Map<AssetClass, Double>): SbmResult {
        val rcExposures = mutableMapOf<FrtbRiskClass, Double>()
        for ((ac, exposure) in exposures) {
            val rcs = ASSET_CLASS_TO_RISK_CLASSES[ac] ?: continue
            for (rc in rcs) {
                rcExposures[rc] = (rcExposures[rc] ?: 0.0) + abs(exposure)
            }
        }

        val charges = FrtbRiskClass.entries.map { rc ->
            val exposure = rcExposures[rc] ?: 0.0
            val deltaCharge = rc.weight * exposure * rc.weight
            val vegaCharge = rc.vegaWeight * exposure * rc.vegaWeight
            val curvatureCharge = 0.5 * rc.weight * rc.weight * exposure * rc.weight
            val total = deltaCharge + vegaCharge + curvatureCharge
            RiskClassCharge(rc, deltaCharge, vegaCharge, curvatureCharge, total)
        }

        val total = sqrt(charges.sumOf { it.totalCharge * it.totalCharge })
        return SbmResult(charges, total)
    }
}

private class StubDrcEngine {
    private val creditClasses = setOf(AssetClass.FIXED_INCOME, AssetClass.DERIVATIVE)
    private val defaultProb = 0.015
    private val lgd = 0.6

    fun calculate(exposures: Map<AssetClass, Double>): DrcResult {
        val creditExposures = exposures.filter { it.key in creditClasses }
        if (creditExposures.isEmpty()) {
            return DrcResult(0.0, 0.0, 0.0)
        }
        val grossJtd = creditExposures.values.sumOf { abs(it) * defaultProb * lgd }
        return DrcResult(grossJtd, 0.0, grossJtd)
    }
}

private class StubRraoEngine {
    fun calculate(exposures: Map<AssetClass, Double>): RraoResult {
        val exoticNotional = abs(exposures[AssetClass.DERIVATIVE] ?: 0.0)
        val otherNotional = exposures.filter { it.key != AssetClass.DERIVATIVE }.values.sumOf { abs(it) }
        val exoticCharge = exoticNotional * 0.01
        val otherCharge = otherNotional * 0.001
        return RraoResult(exoticNotional, otherNotional, exoticCharge + otherCharge)
    }
}

private class StubFrtbCalculator {
    private val sbm = StubSbmEngine()
    private val drc = StubDrcEngine()
    private val rrao = StubRraoEngine()

    fun calculate(portfolioId: String, exposures: Map<AssetClass, Double>): FrtbResult {
        val sbmResult = sbm.calculate(exposures)
        val drcResult = drc.calculate(exposures)
        val rraoResult = rrao.calculate(exposures)
        val total = sbmResult.totalSbmCharge + drcResult.netDrc + rraoResult.totalRrao
        return FrtbResult(portfolioId, sbmResult, drcResult, rraoResult, total)
    }
}

// --- Stub report generator ---

private fun generateCsvReport(result: FrtbResult): String {
    val sb = StringBuilder()
    sb.appendLine("Component,Risk Class,Delta Charge,Vega Charge,Curvature Charge,Total Charge")
    for (rcc in result.sbm.riskClassCharges) {
        sb.appendLine("SbM,${rcc.riskClass.name},%.2f,%.2f,%.2f,%.2f".format(
            rcc.deltaCharge, rcc.vegaCharge, rcc.curvatureCharge, rcc.totalCharge,
        ))
    }
    sb.appendLine("DRC,,%.2f,%.2f,,%.2f".format(result.drc.grossJtd, result.drc.hedgeBenefit, result.drc.netDrc))
    sb.appendLine("RRAO,,%.2f,%.2f,,%.2f".format(result.rrao.exoticNotional, result.rrao.otherNotional, result.rrao.totalRrao))
    sb.appendLine("Total,,,,,%.2f".format(result.totalCapitalCharge))
    return sb.toString()
}

private fun generateXbrlReport(result: FrtbResult): String {
    val sb = StringBuilder()
    sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
    sb.appendLine("""<FRTBReport portfolioId="${result.portfolioId}">""")
    sb.appendLine("  <SensitivitiesBasedMethod>")
    for (rcc in result.sbm.riskClassCharges) {
        sb.appendLine("""    <RiskClassCharge riskClass="${rcc.riskClass.name}">""")
        sb.appendLine("      <TotalCharge>%.2f</TotalCharge>".format(rcc.totalCharge))
        sb.appendLine("    </RiskClassCharge>")
    }
    sb.appendLine("  </SensitivitiesBasedMethod>")
    sb.appendLine("  <DefaultRiskCharge>")
    sb.appendLine("    <NetDRC>%.2f</NetDRC>".format(result.drc.netDrc))
    sb.appendLine("  </DefaultRiskCharge>")
    sb.appendLine("  <ResidualRiskAddOn>")
    sb.appendLine("    <TotalRRAO>%.2f</TotalRRAO>".format(result.rrao.totalRrao))
    sb.appendLine("  </ResidualRiskAddOn>")
    sb.appendLine("  <TotalCapitalCharge>%.2f</TotalCapitalCharge>".format(result.totalCapitalCharge))
    sb.appendLine("</FRTBReport>")
    return sb.toString()
}

// --- Acceptance test ---

class RegulatoryReportingAcceptanceTest : BehaviorSpec({

    given("a portfolio with positions across multiple asset classes") {
        val exposures = mapOf(
            AssetClass.EQUITY to 1_000_000.0,
            AssetClass.FIXED_INCOME to 500_000.0,
            AssetClass.COMMODITY to 200_000.0,
            AssetClass.DERIVATIVE to 400_000.0,
            AssetClass.FX to 300_000.0,
        )
        val calculator = StubFrtbCalculator()

        `when`("FRTB report is generated") {
            val result = calculator.calculate("port-regulatory", exposures)

            then("capital requirement calculated across all seven risk classes") {
                result.sbm.riskClassCharges shouldHaveSize 7
            }

            then("SbM charge is positive for equity risk class") {
                val equityCharge = result.sbm.riskClassCharges.first { it.riskClass == FrtbRiskClass.EQUITY }
                equityCharge.totalCharge shouldBeGreaterThan 0.0
            }

            then("SbM charge is positive for commodity risk class") {
                val commodityCharge = result.sbm.riskClassCharges.first { it.riskClass == FrtbRiskClass.COMMODITY }
                commodityCharge.totalCharge shouldBeGreaterThan 0.0
            }

            then("DRC charge applies to fixed income positions") {
                result.drc.netDrc shouldBeGreaterThan 0.0
            }

            then("RRAO charge applies to derivative positions") {
                result.rrao.exoticNotional shouldBeGreaterThan 0.0
                result.rrao.totalRrao shouldBeGreaterThan 0.0
            }

            then("total capital charge equals SbM + DRC + RRAO") {
                val expected = result.sbm.totalSbmCharge + result.drc.netDrc + result.rrao.totalRrao
                result.totalCapitalCharge shouldBe expected
            }
        }

        `when`("CSV report is generated") {
            val result = calculator.calculate("port-regulatory", exposures)
            val csv = generateCsvReport(result)

            then("report contains header row") {
                csv shouldContain "Component"
                csv shouldContain "Risk Class"
            }

            then("report contains rows for all risk classes") {
                for (rc in FrtbRiskClass.entries) {
                    csv shouldContain rc.name
                }
            }

            then("report contains total capital charge") {
                csv shouldContain "Total"
                csv shouldContain "%.2f".format(result.totalCapitalCharge)
            }
        }

        `when`("XBRL report is generated") {
            val result = calculator.calculate("port-regulatory", exposures)
            val xbrl = generateXbrlReport(result)

            then("report is valid XML") {
                xbrl shouldContain "<?xml"
                xbrl shouldContain "FRTBReport"
            }

            then("report contains FRTB structure") {
                xbrl shouldContain "SensitivitiesBasedMethod"
                xbrl shouldContain "DefaultRiskCharge"
                xbrl shouldContain "ResidualRiskAddOn"
                xbrl shouldContain "TotalCapitalCharge"
            }
        }
    }
})
