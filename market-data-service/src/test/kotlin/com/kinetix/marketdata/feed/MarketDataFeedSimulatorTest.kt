package com.kinetix.marketdata.feed

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.MarketDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency
import kotlin.random.Random

class MarketDataFeedSimulatorTest : FunSpec({

    val USD = Currency.getInstance("USD")
    val GBP = Currency.getInstance("GBP")

    test("tick produces one data point per instrument") {
        val simulator = MarketDataFeedSimulator(
            seeds = listOf(
                InstrumentSeed(InstrumentId("AAPL"), BigDecimal("150.00"), USD),
                InstrumentSeed(InstrumentId("MSFT"), BigDecimal("400.00"), USD),
                InstrumentSeed(InstrumentId("BP"), BigDecimal("5.50"), GBP),
            ),
            random = Random(42),
        )

        val points = simulator.tick(Instant.parse("2025-01-15T10:00:00Z"), MarketDataSource.INTERNAL)
        points shouldHaveSize 3
        points.map { it.instrumentId.value }.toSet() shouldBe setOf("AAPL", "MSFT", "BP")
    }

    test("tick uses provided timestamp and source") {
        val simulator = MarketDataFeedSimulator(
            seeds = listOf(InstrumentSeed(InstrumentId("AAPL"), BigDecimal("150.00"), USD)),
            random = Random(42),
        )

        val timestamp = Instant.parse("2025-06-01T14:30:00Z")
        val points = simulator.tick(timestamp, MarketDataSource.BLOOMBERG)
        points[0].timestamp shouldBe timestamp
        points[0].source shouldBe MarketDataSource.BLOOMBERG
    }

    test("tick preserves instrument currency") {
        val simulator = MarketDataFeedSimulator(
            seeds = listOf(
                InstrumentSeed(InstrumentId("AAPL"), BigDecimal("150.00"), USD),
                InstrumentSeed(InstrumentId("BP"), BigDecimal("5.50"), GBP),
            ),
            random = Random(42),
        )

        val points = simulator.tick(Instant.parse("2025-01-15T10:00:00Z"), MarketDataSource.INTERNAL)
        val aapl = points.first { it.instrumentId.value == "AAPL" }
        val bp = points.first { it.instrumentId.value == "BP" }
        aapl.price.currency shouldBe USD
        bp.price.currency shouldBe GBP
    }

    test("prices stay positive after many ticks") {
        val simulator = MarketDataFeedSimulator(
            seeds = listOf(InstrumentSeed(InstrumentId("AAPL"), BigDecimal("1.00"), USD)),
            maxChangePercent = 50.0,
            random = Random(123),
        )

        repeat(1000) { i ->
            val points = simulator.tick(
                Instant.parse("2025-01-15T10:00:00Z").plusSeconds(i.toLong()),
                MarketDataSource.INTERNAL,
            )
            points[0].price.amount shouldBeGreaterThan BigDecimal.ZERO
        }
    }

    test("deterministic output with same random seed") {
        fun createSimulator() = MarketDataFeedSimulator(
            seeds = listOf(InstrumentSeed(InstrumentId("AAPL"), BigDecimal("150.00"), USD)),
            random = Random(99),
        )

        val sim1 = createSimulator()
        val sim2 = createSimulator()

        val ts = Instant.parse("2025-01-15T10:00:00Z")
        repeat(10) { i ->
            val t = ts.plusSeconds(i.toLong())
            val p1 = sim1.tick(t, MarketDataSource.INTERNAL)
            val p2 = sim2.tick(t, MarketDataSource.INTERNAL)
            p1[0].price.amount.compareTo(p2[0].price.amount) shouldBe 0
        }
    }

    test("price changes are bounded by maxChangePercent") {
        val initialPrice = BigDecimal("100.00")
        val maxChange = 2.0
        val simulator = MarketDataFeedSimulator(
            seeds = listOf(InstrumentSeed(InstrumentId("AAPL"), initialPrice, USD)),
            maxChangePercent = maxChange,
            random = Random(42),
        )

        val points = simulator.tick(Instant.parse("2025-01-15T10:00:00Z"), MarketDataSource.INTERNAL)
        val newPrice = points[0].price.amount
        val changePercent = newPrice.subtract(initialPrice)
            .divide(initialPrice, 10, java.math.RoundingMode.HALF_UP)
            .abs()
            .multiply(BigDecimal("100"))

        changePercent shouldBe io.kotest.matchers.comparables.beLessThanOrEqualTo(BigDecimal.valueOf(maxChange))
    }
})
