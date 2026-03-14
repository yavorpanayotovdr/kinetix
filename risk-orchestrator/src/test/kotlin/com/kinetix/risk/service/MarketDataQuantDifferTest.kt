package com.kinetix.risk.service

import com.kinetix.risk.model.ChangeMagnitude
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MarketDataQuantDifferTest : FunSpec({

    val differ = MarketDataQuantDiffer()

    context("scalar data types (SPOT_PRICE, RISK_FREE_RATE, DIVIDEND_YIELD, CREDIT_SPREAD)") {

        test("LARGE when relative price change exceeds 5%") {
            val base = scalarPayload("SPOT_PRICE", "AAPL", "EQUITY", 100.0)
            val target = scalarPayload("SPOT_PRICE", "AAPL", "EQUITY", 106.0) // +6%
            differ.computeMagnitude("SPOT_PRICE", base, target) shouldBe ChangeMagnitude.LARGE
        }

        test("MEDIUM when relative price change is between 1% and 5%") {
            val base = scalarPayload("SPOT_PRICE", "AAPL", "EQUITY", 100.0)
            val target = scalarPayload("SPOT_PRICE", "AAPL", "EQUITY", 103.0) // +3%
            differ.computeMagnitude("SPOT_PRICE", base, target) shouldBe ChangeMagnitude.MEDIUM
        }

        test("SMALL when relative price change is at most 1%") {
            val base = scalarPayload("SPOT_PRICE", "AAPL", "EQUITY", 100.0)
            val target = scalarPayload("SPOT_PRICE", "AAPL", "EQUITY", 100.5) // +0.5%
            differ.computeMagnitude("SPOT_PRICE", base, target) shouldBe ChangeMagnitude.SMALL
        }

        test("handles negative price moves as LARGE") {
            val base = scalarPayload("SPOT_PRICE", "AAPL", "EQUITY", 100.0)
            val target = scalarPayload("SPOT_PRICE", "AAPL", "EQUITY", 90.0) // -10%
            differ.computeMagnitude("SPOT_PRICE", base, target) shouldBe ChangeMagnitude.LARGE
        }

        test("base value of zero uses absolute comparison and returns LARGE") {
            val base = scalarPayload("RISK_FREE_RATE", "USD", "RATES", 0.0)
            val target = scalarPayload("RISK_FREE_RATE", "USD", "RATES", 0.02)
            differ.computeMagnitude("RISK_FREE_RATE", base, target) shouldBe ChangeMagnitude.LARGE
        }

        test("works for CREDIT_SPREAD data type") {
            val base = scalarPayload("CREDIT_SPREAD", "AAPL", "CREDIT", 0.015)
            val target = scalarPayload("CREDIT_SPREAD", "AAPL", "CREDIT", 0.025) // +66%
            differ.computeMagnitude("CREDIT_SPREAD", base, target) shouldBe ChangeMagnitude.LARGE
        }

        test("boundary: exactly 5% is MEDIUM not LARGE") {
            val base = scalarPayload("SPOT_PRICE", "AAPL", "EQUITY", 100.0)
            val target = scalarPayload("SPOT_PRICE", "AAPL", "EQUITY", 105.0) // exactly 5%
            differ.computeMagnitude("SPOT_PRICE", base, target) shouldBe ChangeMagnitude.MEDIUM
        }

        test("boundary: exactly 1% is SMALL not MEDIUM") {
            val base = scalarPayload("SPOT_PRICE", "AAPL", "EQUITY", 100.0)
            val target = scalarPayload("SPOT_PRICE", "AAPL", "EQUITY", 101.0) // exactly 1%
            differ.computeMagnitude("SPOT_PRICE", base, target) shouldBe ChangeMagnitude.SMALL
        }
    }

    context("curve data types (YIELD_CURVE, FORWARD_CURVE)") {

        test("LARGE when mean absolute point change exceeds 5%") {
            val base = curvePayload("YIELD_CURVE", "USD", "RATES", listOf("1M" to 0.04, "3M" to 0.045, "6M" to 0.05))
            val target = curvePayload("YIELD_CURVE", "USD", "RATES", listOf("1M" to 0.044, "3M" to 0.05, "6M" to 0.056))
            // changes: +10%, +11%, +12% → mean ~11% → LARGE
            differ.computeMagnitude("YIELD_CURVE", base, target) shouldBe ChangeMagnitude.LARGE
        }

        test("MEDIUM when mean absolute point change is between 1% and 5%") {
            val base = curvePayload("YIELD_CURVE", "USD", "RATES", listOf("1M" to 0.04, "3M" to 0.045))
            val target = curvePayload("YIELD_CURVE", "USD", "RATES", listOf("1M" to 0.041, "3M" to 0.046))
            // changes: +2.5%, +2.2% → mean ~2.4% → MEDIUM
            differ.computeMagnitude("YIELD_CURVE", base, target) shouldBe ChangeMagnitude.MEDIUM
        }

        test("SMALL when mean absolute point change is at most 1%") {
            val base = curvePayload("YIELD_CURVE", "USD", "RATES", listOf("1M" to 0.04, "3M" to 0.045))
            val target = curvePayload("YIELD_CURVE", "USD", "RATES", listOf("1M" to 0.0402, "3M" to 0.0452))
            // changes: +0.5%, +0.4% → mean ~0.45% → SMALL
            differ.computeMagnitude("YIELD_CURVE", base, target) shouldBe ChangeMagnitude.SMALL
        }

        test("handles curves with different tenors by matching only common tenors") {
            val base = curvePayload("FORWARD_CURVE", "USD", "RATES", listOf("1M" to 0.04, "3M" to 0.045))
            val target = curvePayload("FORWARD_CURVE", "USD", "RATES", listOf("1M" to 0.044, "3M" to 0.05, "6M" to 0.055))
            // Common tenors: 1M (+10%), 3M (+11%) → mean ~10.5% → LARGE
            differ.computeMagnitude("FORWARD_CURVE", base, target) shouldBe ChangeMagnitude.LARGE
        }
    }

    context("volatility surface (VOLATILITY_SURFACE)") {

        test("LARGE when ATM vol shift at nearest maturity exceeds 5%") {
            // 3x3 surface: rows=maturities, cols=moneyness (strikes relative to spot)
            val base = matrixPayload("VOLATILITY_SURFACE", "AAPL", "EQUITY",
                rows = listOf("30", "60", "90"),
                columns = listOf("0.95", "1.00", "1.05"),
                values = listOf(0.25, 0.20, 0.27, 0.24, 0.19, 0.26, 0.23, 0.18, 0.25))
            val target = matrixPayload("VOLATILITY_SURFACE", "AAPL", "EQUITY",
                rows = listOf("30", "60", "90"),
                columns = listOf("0.95", "1.00", "1.05"),
                values = listOf(0.25, 0.24, 0.27, 0.24, 0.19, 0.26, 0.23, 0.18, 0.25))
            // ATM column (1.00) at nearest maturity (30d): 0.20 → 0.24 = +20% → LARGE
            differ.computeMagnitude("VOLATILITY_SURFACE", base, target) shouldBe ChangeMagnitude.LARGE
        }

        test("SMALL when ATM vol shift at nearest maturity is at most 1%") {
            val base = matrixPayload("VOLATILITY_SURFACE", "AAPL", "EQUITY",
                rows = listOf("30", "60"),
                columns = listOf("0.95", "1.00", "1.05"),
                values = listOf(0.25, 0.20, 0.27, 0.24, 0.19, 0.26))
            val target = matrixPayload("VOLATILITY_SURFACE", "AAPL", "EQUITY",
                rows = listOf("30", "60"),
                columns = listOf("0.95", "1.00", "1.05"),
                values = listOf(0.25, 0.2015, 0.27, 0.24, 0.19, 0.26))
            // ATM at 30d: 0.20 → 0.2015 = +0.75% → SMALL
            differ.computeMagnitude("VOLATILITY_SURFACE", base, target) shouldBe ChangeMagnitude.SMALL
        }
    }

    context("correlation matrix (CORRELATION_MATRIX)") {

        test("LARGE when mean absolute off-diagonal change exceeds 0.10") {
            val base = matrixPayload("CORRELATION_MATRIX", "PORTFOLIO", "MULTI",
                rows = listOf("AAPL", "GOOGL", "MSFT"),
                columns = listOf("AAPL", "GOOGL", "MSFT"),
                values = listOf(1.0, 0.5, 0.3, 0.5, 1.0, 0.4, 0.3, 0.4, 1.0))
            val target = matrixPayload("CORRELATION_MATRIX", "PORTFOLIO", "MULTI",
                rows = listOf("AAPL", "GOOGL", "MSFT"),
                columns = listOf("AAPL", "GOOGL", "MSFT"),
                values = listOf(1.0, 0.7, 0.5, 0.7, 1.0, 0.6, 0.5, 0.6, 1.0))
            // Off-diagonal changes: |0.2|, |0.2|, |0.2|, |0.2|, |0.2|, |0.2| → mean 0.2 > 0.10 → LARGE
            differ.computeMagnitude("CORRELATION_MATRIX", base, target) shouldBe ChangeMagnitude.LARGE
        }

        test("MEDIUM when mean absolute off-diagonal change is between 0.03 and 0.10") {
            val base = matrixPayload("CORRELATION_MATRIX", "PORTFOLIO", "MULTI",
                rows = listOf("AAPL", "GOOGL"),
                columns = listOf("AAPL", "GOOGL"),
                values = listOf(1.0, 0.5, 0.5, 1.0))
            val target = matrixPayload("CORRELATION_MATRIX", "PORTFOLIO", "MULTI",
                rows = listOf("AAPL", "GOOGL"),
                columns = listOf("AAPL", "GOOGL"),
                values = listOf(1.0, 0.55, 0.55, 1.0))
            // Off-diagonal changes: |0.05|, |0.05| → mean 0.05 → MEDIUM
            differ.computeMagnitude("CORRELATION_MATRIX", base, target) shouldBe ChangeMagnitude.MEDIUM
        }

        test("SMALL when mean absolute off-diagonal change is at most 0.03") {
            val base = matrixPayload("CORRELATION_MATRIX", "PORTFOLIO", "MULTI",
                rows = listOf("AAPL", "GOOGL"),
                columns = listOf("AAPL", "GOOGL"),
                values = listOf(1.0, 0.5, 0.5, 1.0))
            val target = matrixPayload("CORRELATION_MATRIX", "PORTFOLIO", "MULTI",
                rows = listOf("AAPL", "GOOGL"),
                columns = listOf("AAPL", "GOOGL"),
                values = listOf(1.0, 0.52, 0.52, 1.0))
            // Off-diagonal changes: |0.02|, |0.02| → mean 0.02 → SMALL
            differ.computeMagnitude("CORRELATION_MATRIX", base, target) shouldBe ChangeMagnitude.SMALL
        }
    }

    context("historical prices (HISTORICAL_PRICES)") {

        test("classifies based on most recent point change percentage") {
            val base = timeSeriesPayload("HISTORICAL_PRICES", "AAPL", "EQUITY", listOf(
                "2026-01-01T00:00:00Z" to 100.0,
                "2026-01-02T00:00:00Z" to 102.0,
            ))
            val target = timeSeriesPayload("HISTORICAL_PRICES", "AAPL", "EQUITY", listOf(
                "2026-01-01T00:00:00Z" to 100.0,
                "2026-01-02T00:00:00Z" to 102.0,
                "2026-01-03T00:00:00Z" to 110.0,
            ))
            // Most recent in target vs most recent in base: 110 vs 102 → ~7.8% → LARGE
            differ.computeMagnitude("HISTORICAL_PRICES", base, target) shouldBe ChangeMagnitude.LARGE
        }
    }

    context("unknown data type") {

        test("returns MEDIUM as fallback for unrecognized data type") {
            val base = scalarPayload("UNKNOWN_TYPE", "X", "OTHER", 100.0)
            val target = scalarPayload("UNKNOWN_TYPE", "X", "OTHER", 200.0)
            differ.computeMagnitude("UNKNOWN_TYPE", base, target) shouldBe ChangeMagnitude.MEDIUM
        }
    }

    context("malformed payloads") {

        test("returns MEDIUM when payload cannot be parsed") {
            differ.computeMagnitude("SPOT_PRICE", "not json", "also not json") shouldBe ChangeMagnitude.MEDIUM
        }

        test("returns MEDIUM when payload is empty") {
            differ.computeMagnitude("SPOT_PRICE", "", "") shouldBe ChangeMagnitude.MEDIUM
        }
    }
})

// --- Test Helpers ---

private fun scalarPayload(dataType: String, instrumentId: String, assetClass: String, value: Double): String =
    """{"dataType":"$dataType","instrumentId":"$instrumentId","assetClass":"$assetClass","value":$value}"""

private fun curvePayload(dataType: String, instrumentId: String, assetClass: String, points: List<Pair<String, Double>>): String {
    val pointsJson = points.joinToString(",") { (tenor, value) ->
        """{"tenor":"$tenor","value":$value}"""
    }
    return """{"dataType":"$dataType","instrumentId":"$instrumentId","assetClass":"$assetClass","points":[$pointsJson]}"""
}

private fun matrixPayload(
    dataType: String, instrumentId: String, assetClass: String,
    rows: List<String>, columns: List<String>, values: List<Double>,
): String {
    val rowsJson = rows.joinToString(",") { "\"$it\"" }
    val colsJson = columns.joinToString(",") { "\"$it\"" }
    val valsJson = values.joinToString(",")
    return """{"dataType":"$dataType","instrumentId":"$instrumentId","assetClass":"$assetClass","rows":[$rowsJson],"columns":[$colsJson],"values":[$valsJson]}"""
}

private fun timeSeriesPayload(dataType: String, instrumentId: String, assetClass: String, points: List<Pair<String, Double>>): String {
    val pointsJson = points.joinToString(",") { (ts, value) ->
        """{"timestamp":"$ts","value":$value}"""
    }
    return """{"dataType":"$dataType","instrumentId":"$instrumentId","assetClass":"$assetClass","points":[$pointsJson]}"""
}
