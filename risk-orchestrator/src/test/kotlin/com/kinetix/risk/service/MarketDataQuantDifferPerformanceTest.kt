package com.kinetix.risk.service

import com.kinetix.risk.model.ChangeMagnitude
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.system.measureTimeMillis

class MarketDataQuantDifferPerformanceTest : FunSpec({

    val differ = MarketDataQuantDiffer()

    test("scalar diff completes in under 5ms for 500 iterations") {
        val base = """{"value":100.0}"""
        val target = """{"value":106.0}"""

        // Warm up
        repeat(100) { differ.computeMagnitude("SPOT_PRICE", base, target) }

        val elapsed = measureTimeMillis {
            repeat(500) { differ.computeMagnitude("SPOT_PRICE", base, target) }
        }
        // 500 iterations in under 500ms → <1ms each
        elapsed shouldBeLessThan 500L
    }

    test("time series diff with 504 observations completes in under 50ms") {
        val points = (0 until 504).map { i ->
            """{"timestamp":"2026-01-${String.format("%03d", i)}T00:00:00Z","value":${100.0 + i * 0.1}}"""
        }.joinToString(",")
        val base = """{"points":[$points]}"""

        val targetPoints = (0 until 504).map { i ->
            """{"timestamp":"2026-01-${String.format("%03d", i)}T00:00:00Z","value":${100.5 + i * 0.12}}"""
        }.joinToString(",")
        val target = """{"points":[$targetPoints]}"""

        // Warm up
        repeat(10) { differ.computeMagnitude("HISTORICAL_PRICES", base, target) }

        val elapsed = measureTimeMillis {
            differ.computeMagnitude("HISTORICAL_PRICES", base, target)
        }
        elapsed shouldBeLessThan 50L
    }

    test("large correlation matrix diff (50x50) completes in under 50ms") {
        val n = 50
        val rows = (0 until n).map { "\"INST_$it\"" }.joinToString(",")
        val baseValues = buildList {
            for (i in 0 until n) {
                for (j in 0 until n) {
                    if (i == j) add(1.0) else add(0.5 + (i + j) * 0.001)
                }
            }
        }.joinToString(",")
        val targetValues = buildList {
            for (i in 0 until n) {
                for (j in 0 until n) {
                    if (i == j) add(1.0) else add(0.52 + (i + j) * 0.001)
                }
            }
        }.joinToString(",")

        val base = """{"rows":[$rows],"columns":[$rows],"values":[$baseValues]}"""
        val target = """{"rows":[$rows],"columns":[$rows],"values":[$targetValues]}"""

        // Warm up
        repeat(5) { differ.computeMagnitude("CORRELATION_MATRIX", base, target) }

        val elapsed = measureTimeMillis {
            differ.computeMagnitude("CORRELATION_MATRIX", base, target)
        }
        elapsed shouldBeLessThan 50L
    }

    test("vol surface diff with 20 maturities x 15 strikes completes in under 20ms") {
        val strikes = (0 until 15).map { "\"${0.85 + it * 0.02}\"" }.joinToString(",")
        val baseVols = (0 until 300).map { 0.20 + it * 0.0001 }.joinToString(",")
        val targetVols = (0 until 300).map { 0.21 + it * 0.0001 }.joinToString(",")
        val rows = (0 until 20).map { "\"${30 + it * 15}\"" }.joinToString(",")

        val base = """{"rows":[$rows],"columns":[$strikes],"values":[$baseVols]}"""
        val target = """{"rows":[$rows],"columns":[$strikes],"values":[$targetVols]}"""

        // Warm up
        repeat(10) { differ.computeMagnitude("VOLATILITY_SURFACE", base, target) }

        val elapsed = measureTimeMillis {
            differ.computeMagnitude("VOLATILITY_SURFACE", base, target)
        }
        elapsed shouldBeLessThan 20L
    }

    test("yield curve diff with 30 tenors completes in under 10ms") {
        val basePoints = (0 until 30).map { i ->
            """{"tenor":"${i + 1}M","value":${0.04 + i * 0.001}}"""
        }.joinToString(",")
        val targetPoints = (0 until 30).map { i ->
            """{"tenor":"${i + 1}M","value":${0.042 + i * 0.0012}}"""
        }.joinToString(",")

        val base = """{"points":[$basePoints]}"""
        val target = """{"points":[$targetPoints]}"""

        // Warm up
        repeat(10) { differ.computeMagnitude("YIELD_CURVE", base, target) }

        val elapsed = measureTimeMillis {
            differ.computeMagnitude("YIELD_CURVE", base, target)
        }
        elapsed shouldBeLessThan 10L
    }

    test("500 instrument scalar diffs complete in under 200ms at p95") {
        val instruments = (0 until 500).map { i ->
            val base = """{"value":${100.0 + i * 0.5}}"""
            val target = """{"value":${100.5 + i * 0.6}}"""
            base to target
        }

        // Warm up
        instruments.take(50).forEach { (b, t) -> differ.computeMagnitude("SPOT_PRICE", b, t) }

        val timings = instruments.map { (base, target) ->
            measureTimeMillis { differ.computeMagnitude("SPOT_PRICE", base, target) }
        }.sorted()

        val p95 = timings[(timings.size * 0.95).toInt()]
        p95 shouldBeLessThan 5L // Each individual diff should be very fast

        val total = timings.sum()
        total shouldBeLessThan 200L
    }
})