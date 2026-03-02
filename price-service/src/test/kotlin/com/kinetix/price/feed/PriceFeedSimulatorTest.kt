package com.kinetix.price.feed

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PriceSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

class PriceFeedSimulatorTest : FunSpec({

    val USD = Currency.getInstance("USD")
    val GBP = Currency.getInstance("GBP")

    test("tick produces one data point per instrument") {
        val simulator = PriceFeedSimulator(
            seeds = listOf(
                InstrumentSeed(InstrumentId("AAPL"), BigDecimal("150.00"), USD),
                InstrumentSeed(InstrumentId("MSFT"), BigDecimal("400.00"), USD),
                InstrumentSeed(InstrumentId("BP"), BigDecimal("5.50"), GBP),
            ),
            random = Random(42),
        )

        val points = simulator.tick(Instant.parse("2025-01-15T10:00:00Z"), PriceSource.INTERNAL)
        points shouldHaveSize 3
        points.map { it.instrumentId.value }.toSet() shouldBe setOf("AAPL", "MSFT", "BP")
    }

    test("tick uses provided timestamp and source") {
        val simulator = PriceFeedSimulator(
            seeds = listOf(InstrumentSeed(InstrumentId("AAPL"), BigDecimal("150.00"), USD)),
            random = Random(42),
        )

        val timestamp = Instant.parse("2025-06-01T14:30:00Z")
        val points = simulator.tick(timestamp, PriceSource.BLOOMBERG)
        points[0].timestamp shouldBe timestamp
        points[0].source shouldBe PriceSource.BLOOMBERG
    }

    test("tick preserves instrument currency") {
        val simulator = PriceFeedSimulator(
            seeds = listOf(
                InstrumentSeed(InstrumentId("AAPL"), BigDecimal("150.00"), USD),
                InstrumentSeed(InstrumentId("BP"), BigDecimal("5.50"), GBP),
            ),
            random = Random(42),
        )

        val points = simulator.tick(Instant.parse("2025-01-15T10:00:00Z"), PriceSource.INTERNAL)
        val aapl = points.first { it.instrumentId.value == "AAPL" }
        val bp = points.first { it.instrumentId.value == "BP" }
        aapl.price.currency shouldBe USD
        bp.price.currency shouldBe GBP
    }

    test("prices stay positive after many ticks") {
        val simulator = PriceFeedSimulator(
            seeds = listOf(InstrumentSeed(InstrumentId("AAPL"), BigDecimal("1.00"), USD)),
            random = Random(123),
        )

        repeat(1000) { i ->
            val points = simulator.tick(
                Instant.parse("2025-01-15T10:00:00Z").plusSeconds(i.toLong()),
                PriceSource.INTERNAL,
            )
            points[0].price.amount shouldBeGreaterThan BigDecimal.ZERO
        }
    }

    test("deterministic output with same random seed") {
        fun createSimulator() = PriceFeedSimulator(
            seeds = listOf(InstrumentSeed(InstrumentId("AAPL"), BigDecimal("150.00"), USD)),
            random = Random(99),
        )

        val sim1 = createSimulator()
        val sim2 = createSimulator()

        val ts = Instant.parse("2025-01-15T10:00:00Z")
        repeat(10) { i ->
            val t = ts.plusSeconds(i.toLong())
            val p1 = sim1.tick(t, PriceSource.INTERNAL)
            val p2 = sim2.tick(t, PriceSource.INTERNAL)
            p1[0].price.amount.compareTo(p2[0].price.amount) shouldBe 0
        }
    }

    test("per-tick price change is consistent with configured volatility") {
        val initialPrice = BigDecimal("100.00")
        val simulator = PriceFeedSimulator(
            seeds = listOf(InstrumentSeed(InstrumentId("AAPL"), initialPrice, USD, AssetClass.EQUITY)),
            random = Random(42),
        )

        val points = simulator.tick(Instant.parse("2025-01-15T10:00:00Z"), PriceSource.INTERNAL)
        val newPrice = points[0].price.amount
        val changePercent = abs(newPrice.toDouble() - initialPrice.toDouble()) / initialPrice.toDouble() * 100

        // A single 1-second GBM tick with 20% annual vol produces tiny moves
        // sigma*sqrt(dt) ~ 0.20 * sqrt(1/5.9e6) ~ 0.008% per tick
        changePercent shouldBeLessThan 1.0
    }

    // --- Asset-class-aware simulation tests ---

    test("equities exhibit higher volatility than fixed income over many ticks") {
        // Equity annual vol = 20%, Fixed Income = 5%, so equity stdDev should be > 2x
        val equitySeed = InstrumentSeed(InstrumentId("EQ"), BigDecimal("100.00"), USD, AssetClass.EQUITY)
        val bondSeed = InstrumentSeed(InstrumentId("BOND"), BigDecimal("100.00"), USD, AssetClass.FIXED_INCOME)

        val sim = PriceFeedSimulator(
            seeds = listOf(equitySeed, bondSeed),
            random = Random(42),
        )

        val eqReturns = mutableListOf<Double>()
        val bondReturns = mutableListOf<Double>()
        var prevEq = 100.0
        var prevBond = 100.0

        repeat(10_000) { i ->
            val points = sim.tick(Instant.parse("2025-01-15T10:00:00Z").plusSeconds(i.toLong()), PriceSource.INTERNAL)
            val eq = points.first { it.instrumentId.value == "EQ" }.price.amount.toDouble()
            val bond = points.first { it.instrumentId.value == "BOND" }.price.amount.toDouble()
            eqReturns.add(ln(eq / prevEq))
            bondReturns.add(ln(bond / prevBond))
            prevEq = eq
            prevBond = bond
        }

        stdDev(eqReturns) shouldBeGreaterThan (stdDev(bondReturns) * 2.0)
    }

    test("FX prices mean-revert toward the seed price") {
        // OU with theta=5.0 should keep the mean-of-path close to the seed
        val seedPrice = BigDecimal("1.0850")
        val fxSeed = InstrumentSeed(InstrumentId("EURUSD"), seedPrice, USD, AssetClass.FX)

        val sim = PriceFeedSimulator(
            seeds = listOf(fxSeed),
            random = Random(42),
        )

        val prices = mutableListOf<Double>()
        repeat(10_000) { i ->
            val points = sim.tick(Instant.parse("2025-01-15T10:00:00Z").plusSeconds(i.toLong()), PriceSource.INTERNAL)
            prices.add(points[0].price.amount.toDouble())
        }

        // Mean of all observed prices should be within 2% of the seed
        val meanPrice = prices.average()
        val deviation = abs(meanPrice - seedPrice.toDouble()) / seedPrice.toDouble()
        deviation shouldBeLessThan 0.02
    }

    test("fixed income prices mean-revert toward the seed price") {
        val seedPrice = BigDecimal("97.10")
        val bondSeed = InstrumentSeed(InstrumentId("US10Y"), seedPrice, USD, AssetClass.FIXED_INCOME)

        val sim = PriceFeedSimulator(
            seeds = listOf(bondSeed),
            random = Random(42),
        )

        val prices = mutableListOf<Double>()
        repeat(10_000) { i ->
            val points = sim.tick(Instant.parse("2025-01-15T10:00:00Z").plusSeconds(i.toLong()), PriceSource.INTERNAL)
            prices.add(points[0].price.amount.toDouble())
        }

        val meanPrice = prices.average()
        val deviation = abs(meanPrice - seedPrice.toDouble()) / seedPrice.toDouble()
        deviation shouldBeLessThan 0.02
    }

    test("commodities exhibit higher volatility than FX") {
        // Commodity annual vol = 25%, FX = 10%, so commodity stdDev > 1.5x FX
        val commoditySeed = InstrumentSeed(InstrumentId("GC"), BigDecimal("2050.00"), USD, AssetClass.COMMODITY)
        val fxSeed = InstrumentSeed(InstrumentId("EURUSD"), BigDecimal("1.0850"), USD, AssetClass.FX)

        val sim = PriceFeedSimulator(
            seeds = listOf(commoditySeed, fxSeed),
            random = Random(42),
        )

        val commodityReturns = mutableListOf<Double>()
        val fxReturns = mutableListOf<Double>()
        var prevCommodity = 2050.0
        var prevFx = 1.0850

        repeat(10_000) { i ->
            val points = sim.tick(Instant.parse("2025-01-15T10:00:00Z").plusSeconds(i.toLong()), PriceSource.INTERNAL)
            val gc = points.first { it.instrumentId.value == "GC" }.price.amount.toDouble()
            val fx = points.first { it.instrumentId.value == "EURUSD" }.price.amount.toDouble()
            commodityReturns.add(ln(gc / prevCommodity))
            fxReturns.add(ln(fx / prevFx))
            prevCommodity = gc
            prevFx = fx
        }

        stdDev(commodityReturns) shouldBeGreaterThan (stdDev(fxReturns) * 1.5)
    }

    test("equity log-returns are approximately normally distributed") {
        // GBM produces log-normal prices, so log-returns should be ~N(0, sigma*sqrt(dt))
        val sim = PriceFeedSimulator(
            seeds = listOf(InstrumentSeed(InstrumentId("AAPL"), BigDecimal("150.00"), USD, AssetClass.EQUITY)),
            random = Random(42),
        )

        val logReturns = mutableListOf<Double>()
        var prevPrice = 150.0

        repeat(50_000) { i ->
            val points = sim.tick(Instant.parse("2025-01-15T10:00:00Z").plusSeconds(i.toLong()), PriceSource.INTERNAL)
            val price = points[0].price.amount.toDouble()
            logReturns.add(ln(price / prevPrice))
            prevPrice = price
        }

        // Mean should be close to zero (drift-corrected GBM)
        abs(logReturns.average()) shouldBeLessThan 1e-4

        // StdDev should be close to sigma * sqrt(dt)
        // sigma=0.20, dt=1/(252*6.5*3600)~1.7e-7, sigma*sqrt(dt)~8.2e-5
        val sd = stdDev(logReturns)
        sd shouldBeGreaterThan 5e-5
        sd shouldBeLessThan 2e-4
    }
})

private fun stdDev(xs: List<Double>): Double {
    val mean = xs.average()
    return sqrt(xs.sumOf { (it - mean).pow(2) } / xs.size)
}
