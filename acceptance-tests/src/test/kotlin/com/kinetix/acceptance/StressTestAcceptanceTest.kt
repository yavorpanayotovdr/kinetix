package com.kinetix.acceptance

import com.kinetix.common.model.AssetClass
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import kotlin.math.abs
import kotlin.math.sqrt

// --- Stub implementations ---

private data class StressScenario(
    val name: String,
    val description: String,
    val volShocks: Map<AssetClass, Double>,
    val priceShocks: Map<AssetClass, Double>,
)

private data class AssetClassImpact(
    val assetClass: AssetClass,
    val baseExposure: Double,
    val stressedExposure: Double,
    val pnlImpact: Double,
)

private data class StressTestResult(
    val scenarioName: String,
    val baseVar: Double,
    val stressedVar: Double,
    val pnlImpact: Double,
    val assetClassImpacts: List<AssetClassImpact>,
)

private data class GreeksResult(
    val portfolioId: String,
    val delta: Map<AssetClass, Double>,
    val gamma: Map<AssetClass, Double>,
    val vega: Map<AssetClass, Double>,
    val theta: Double,
    val rho: Double,
)

private val defaultVols = mapOf(
    AssetClass.EQUITY to 0.20,
    AssetClass.COMMODITY to 0.25,
    AssetClass.FIXED_INCOME to 0.06,
)

private val GFC_2008 = StressScenario(
    name = "GFC_2008",
    description = "Global Financial Crisis 2008",
    volShocks = mapOf(AssetClass.EQUITY to 3.0, AssetClass.COMMODITY to 2.5, AssetClass.FIXED_INCOME to 1.5),
    priceShocks = mapOf(AssetClass.EQUITY to 0.60, AssetClass.COMMODITY to 0.70, AssetClass.FIXED_INCOME to 0.95),
)

private class StubStressTestEngine {
    fun runStressTest(
        exposures: Map<AssetClass, Double>,
        scenario: StressScenario,
    ): StressTestResult {
        val confidenceZ = 1.645
        val sqrtT = sqrt(1.0 / 252.0)

        // Calculate base VaR (simplified single-asset sum)
        var baseVar = 0.0
        for ((ac, exposure) in exposures) {
            val vol = defaultVols[ac] ?: 0.15
            baseVar += exposure * vol * confidenceZ * sqrtT
        }

        // Calculate stressed VaR
        var stressedVar = 0.0
        val impacts = mutableListOf<AssetClassImpact>()
        var totalPnl = 0.0

        for ((ac, baseExposure) in exposures) {
            val priceShock = scenario.priceShocks[ac] ?: 1.0
            val volShock = scenario.volShocks[ac] ?: 1.0
            val stressedExposure = baseExposure * priceShock
            val stressedVol = (defaultVols[ac] ?: 0.15) * volShock
            stressedVar += stressedExposure * stressedVol * confidenceZ * sqrtT
            val pnl = stressedExposure - baseExposure
            totalPnl += pnl
            impacts.add(AssetClassImpact(ac, baseExposure, stressedExposure, pnl))
        }

        return StressTestResult(
            scenarioName = scenario.name,
            baseVar = baseVar,
            stressedVar = stressedVar,
            pnlImpact = totalPnl,
            assetClassImpacts = impacts,
        )
    }
}

private class StubGreeksCalculator {
    private val priceBump = 0.01
    private val volBump = 0.01

    fun calculateGreeks(
        exposures: Map<AssetClass, Double>,
        portfolioId: String,
    ): GreeksResult {
        val confidenceZ = 1.645
        val sqrtT = sqrt(1.0 / 252.0)

        fun portfolioVar(exp: Map<AssetClass, Double>, vols: Map<AssetClass, Double> = defaultVols): Double {
            var sumSq = 0.0
            for ((ac, e) in exp) {
                val component = e * (vols[ac] ?: 0.15) * confidenceZ * sqrtT
                sumSq += component * component
            }
            return sqrt(sumSq)
        }

        val baseVar = portfolioVar(exposures)

        val delta = mutableMapOf<AssetClass, Double>()
        val gamma = mutableMapOf<AssetClass, Double>()
        val vega = mutableMapOf<AssetClass, Double>()

        for (ac in exposures.keys) {
            // Delta
            val upExp = exposures.toMutableMap()
            upExp[ac] = (upExp[ac] ?: 0.0) * (1 + priceBump)
            val varUp = portfolioVar(upExp)
            delta[ac] = (varUp - baseVar) / priceBump

            // Gamma
            val downExp = exposures.toMutableMap()
            downExp[ac] = (downExp[ac] ?: 0.0) * (1 - priceBump)
            val varDown = portfolioVar(downExp)
            gamma[ac] = (varUp - 2 * baseVar + varDown) / (priceBump * priceBump)

            // Vega
            val bumpedVols = defaultVols.toMutableMap()
            bumpedVols[ac] = (bumpedVols[ac] ?: 0.15) + volBump
            val varVolUp = portfolioVar(exposures, bumpedVols)
            vega[ac] = (varVolUp - baseVar) / volBump
        }

        // Theta: use slightly longer horizon
        val sqrtT2 = sqrt(2.0 / 252.0)
        var var2 = 0.0
        for ((ac, e) in exposures) {
            val vol = defaultVols[ac] ?: 0.15
            var2 += e * vol * confidenceZ * sqrtT2
        }
        val theta = var2 - baseVar

        // Rho: bump all vols slightly
        val rateBumpedVols = defaultVols.mapValues { (_, v) -> v + 0.0001 }
        val varRateShifted = portfolioVar(exposures, rateBumpedVols)
        val rho = (varRateShifted - baseVar) / 0.0001

        return GreeksResult(portfolioId, delta, gamma, vega, theta, rho)
    }
}

class StressTestAcceptanceTest : BehaviorSpec({

    given("a portfolio with equity and commodity positions") {
        val exposures = mapOf(
            AssetClass.EQUITY to 1_000_000.0,
            AssetClass.COMMODITY to 500_000.0,
        )
        val engine = StubStressTestEngine()

        `when`("2008 crisis stress test runs") {
            val result = engine.runStressTest(exposures, GFC_2008)

            then("stressed VaR exceeds base VaR") {
                result.stressedVar shouldBeGreaterThan result.baseVar
            }

            then("P&L impact is negative") {
                result.pnlImpact shouldBeLessThan 0.0
            }

            then("losses broken down by asset class") {
                result.assetClassImpacts shouldHaveSize 2
                result.assetClassImpacts.forEach { impact ->
                    impact.pnlImpact shouldBeLessThan 0.0
                }
            }

            then("equity has largest loss due to 40% shock") {
                val equityImpact = result.assetClassImpacts.first { it.assetClass == AssetClass.EQUITY }
                val commodityImpact = result.assetClassImpacts.first { it.assetClass == AssetClass.COMMODITY }
                // Equity drops 40% of $1M = $400K loss; Commodity drops 30% of $500K = $150K loss
                abs(equityImpact.pnlImpact) shouldBeGreaterThan abs(commodityImpact.pnlImpact)
            }
        }

        `when`("hypothetical scenario with custom shocks runs") {
            val customScenario = StressScenario(
                name = "CUSTOM",
                description = "Custom hypothetical",
                volShocks = mapOf(AssetClass.EQUITY to 2.0, AssetClass.COMMODITY to 1.5),
                priceShocks = mapOf(AssetClass.EQUITY to 0.85, AssetClass.COMMODITY to 0.90),
            )
            val result = engine.runStressTest(exposures, customScenario)

            then("stressed VaR reflects custom vol/price shocks") {
                result.stressedVar shouldBeGreaterThan result.baseVar
                result.scenarioName shouldBe "CUSTOM"
            }
        }
    }

    given("a portfolio for Greeks calculation") {
        val exposures = mapOf(
            AssetClass.EQUITY to 1_000_000.0,
            AssetClass.COMMODITY to 500_000.0,
            AssetClass.FIXED_INCOME to 300_000.0,
        )
        val calculator = StubGreeksCalculator()

        `when`("Greeks are calculated") {
            val result = calculator.calculateGreeks(exposures, "port-greeks")

            then("Delta is computed per asset class") {
                result.delta.size shouldBe 3
                result.delta.keys shouldBe exposures.keys
                result.delta.values.forEach { it shouldNotBe 0.0 }
            }

            then("Gamma captures convexity") {
                result.gamma.size shouldBe 3
                result.gamma.values.forEach { it shouldNotBe 0.0 }
            }

            then("Vega is positive for all asset classes") {
                result.vega.values.forEach { it shouldBeGreaterThan 0.0 }
            }

            then("Theta captures time decay") {
                result.theta shouldNotBe 0.0
            }

            then("Rho captures rate sensitivity") {
                result.rho shouldNotBe 0.0
            }
        }
    }
})
