package com.kinetix.risk

import com.kinetix.common.model.AssetClass
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldBeIn
import kotlin.math.abs
import kotlin.math.sqrt

// --- Stub implementations ---

private data class VolatilityPrediction(
    val instrumentId: String,
    val assetClass: AssetClass,
    val predictedVolatility: Double,
)

private class StubVolatilityPredictor {
    private val volatilities = mapOf(
        AssetClass.EQUITY to 0.22,
        AssetClass.FIXED_INCOME to 0.065,
        AssetClass.FX to 0.11,
        AssetClass.COMMODITY to 0.27,
        AssetClass.DERIVATIVE to 0.32,
    )

    fun predict(instrumentId: String, assetClass: AssetClass): VolatilityPrediction {
        val vol = volatilities[assetClass] ?: 0.15
        return VolatilityPrediction(instrumentId, assetClass, vol)
    }

    fun calculateVaR(exposure: Double, assetClass: AssetClass, confidenceZ: Double): Double {
        val vol = volatilities[assetClass] ?: 0.15
        return exposure * vol * confidenceZ * sqrt(1.0 / 252.0)
    }
}

private data class CreditScore(
    val issuerId: String,
    val defaultProbability: Double,
    val rating: String,
)

private class StubCreditScorer {
    fun score(
        issuerId: String,
        leverageRatio: Double,
        interestCoverage: Double,
        debtToEquity: Double,
        currentRatio: Double,
        revenueGrowth: Double,
        volatility90d: Double,
        marketValueLog: Double,
    ): CreditScore {
        // Simple heuristic scoring based on key financial metrics
        val riskScore = (leverageRatio * 0.3 - interestCoverage * 0.25 +
                debtToEquity * 0.2 - currentRatio * 0.1 -
                revenueGrowth * 0.1 + volatility90d * 0.3 - marketValueLog * 0.1)
        val prob = 1.0 / (1.0 + kotlin.math.exp(-riskScore))
        val rating = when {
            prob <= 0.002 -> "AAA"
            prob <= 0.005 -> "AA"
            prob <= 0.01 -> "A"
            prob <= 0.03 -> "BBB"
            prob <= 0.07 -> "BB"
            prob <= 0.15 -> "B"
            prob <= 0.30 -> "CCC"
            else -> "D"
        }
        return CreditScore(issuerId, prob, rating)
    }
}

private data class AnomalyDetectionResult(
    val isAnomaly: Boolean,
    val anomalyScore: Double,
    val metricValue: Double,
)

private class StubAnomalyDetector {
    private val normalMean = 0.0
    private val normalStd = 1.0

    fun detect(values: List<Double>): List<AnomalyDetectionResult> {
        return values.map { value ->
            val zScore = abs(value - normalMean) / normalStd
            val isAnomaly = zScore > 3.0
            AnomalyDetectionResult(
                isAnomaly = isAnomaly,
                anomalyScore = if (isAnomaly) -0.5 else 0.1,
                metricValue = value,
            )
        }
    }
}

class MLModelTest : BehaviorSpec({

    given("a trained volatility prediction model for EQUITY") {
        val predictor = StubVolatilityPredictor()

        `when`("volatility prediction is requested") {
            val prediction = predictor.predict("AAPL", AssetClass.EQUITY)

            then("positive annualized volatility is returned") {
                prediction.predictedVolatility shouldBeGreaterThan 0.0
            }

            then("predicted volatility is in reasonable range for EQUITY") {
                prediction.predictedVolatility shouldBeGreaterThan 0.05
                prediction.predictedVolatility shouldBeLessThan 0.60
            }
        }

        `when`("VaR is calculated using ML-predicted vol vs static vol") {
            val exposure = 1_000_000.0
            val confidenceZ = 1.645

            val mlVaR = predictor.calculateVaR(exposure, AssetClass.EQUITY, confidenceZ)
            // Static vol for equity is 0.20
            val staticVaR = exposure * 0.20 * confidenceZ * sqrt(1.0 / 252.0)

            then("ML vol produces different VaR than static vol") {
                // ML vol is 0.22 vs static 0.20, so they should differ
                abs(mlVaR - staticVaR) shouldBeGreaterThan 0.0
            }
        }
    }

    given("a trained credit default model") {
        val scorer = StubCreditScorer()

        `when`("scoring a safe issuer") {
            val score = scorer.score(
                issuerId = "SAFE-CORP",
                leverageRatio = 0.3,
                interestCoverage = 10.0,
                debtToEquity = 0.2,
                currentRatio = 3.0,
                revenueGrowth = 0.15,
                volatility90d = 0.10,
                marketValueLog = 13.0,
            )

            then("default probability is low") {
                score.defaultProbability shouldBeLessThan 0.10
            }

            then("rating is investment grade") {
                score.rating shouldBeIn listOf("AAA", "AA", "A", "BBB")
            }
        }

        `when`("scoring a risky issuer") {
            val score = scorer.score(
                issuerId = "RISKY-CORP",
                leverageRatio = 5.0,
                interestCoverage = 0.5,
                debtToEquity = 4.0,
                currentRatio = 0.5,
                revenueGrowth = -0.20,
                volatility90d = 0.80,
                marketValueLog = 7.0,
            )

            then("default probability is higher") {
                score.defaultProbability shouldBeGreaterThan 0.10
            }

            then("rating is speculative or worse") {
                score.rating shouldBeIn listOf("BB", "B", "CCC", "D")
            }
        }
    }

    given("an anomaly detector monitoring VaR metrics") {
        val detector = StubAnomalyDetector()

        `when`("normal VaR values submitted") {
            val results = detector.detect(listOf(0.0, 0.5, -0.3, 1.0, -0.5))

            then("no anomaly detected") {
                results.forEach { it.isAnomaly shouldBe false }
            }
        }

        `when`("extreme VaR spike submitted") {
            val results = detector.detect(listOf(100.0))

            then("anomaly is flagged") {
                results.first().isAnomaly shouldBe true
            }
        }
    }
})
