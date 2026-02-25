package com.kinetix.price.metrics

import com.kinetix.common.model.InstrumentId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class PriceStalenessTrackerTest : FunSpec({

    test("registers a gauge for a new instrument on recordUpdate") {
        val registry = SimpleMeterRegistry()
        val tracker = PriceStalenessTracker(registry)

        tracker.recordUpdate(InstrumentId("AAPL"))

        val gauge = registry.find("price_staleness_seconds")
            .tag("instrument_id", "AAPL")
            .gauge()
        gauge.shouldNotBeNull()
    }

    test("staleness is near zero immediately after recordUpdate") {
        val registry = SimpleMeterRegistry()
        val tracker = PriceStalenessTracker(registry)

        tracker.recordUpdate(InstrumentId("AAPL"))

        val gauge = registry.find("price_staleness_seconds")
            .tag("instrument_id", "AAPL")
            .gauge()
        gauge.shouldNotBeNull()
        gauge.value() shouldBeLessThan 2.0
    }

    test("tracks multiple instruments independently") {
        val registry = SimpleMeterRegistry()
        val tracker = PriceStalenessTracker(registry)

        tracker.recordUpdate(InstrumentId("AAPL"))
        tracker.recordUpdate(InstrumentId("GOOGL"))

        val aaplGauge = registry.find("price_staleness_seconds")
            .tag("instrument_id", "AAPL")
            .gauge()
        val googlGauge = registry.find("price_staleness_seconds")
            .tag("instrument_id", "GOOGL")
            .gauge()

        aaplGauge.shouldNotBeNull()
        googlGauge.shouldNotBeNull()
    }

    test("repeated updates for the same instrument reuse the same gauge") {
        val registry = SimpleMeterRegistry()
        val tracker = PriceStalenessTracker(registry)

        tracker.recordUpdate(InstrumentId("AAPL"))
        tracker.recordUpdate(InstrumentId("AAPL"))

        val gauges = registry.find("price_staleness_seconds")
            .tag("instrument_id", "AAPL")
            .gauges()
        gauges.size shouldBe 1
    }

    test("staleness reflects elapsed time since last update using a fixed clock") {
        val baseTime = Instant.parse("2025-06-01T12:00:00Z")
        val registry = SimpleMeterRegistry()
        val mutableClock = MutableClock(baseTime)
        val tracker = PriceStalenessTracker(registry, mutableClock)

        tracker.recordUpdate(InstrumentId("AAPL"))

        mutableClock.advance(30)

        val gauge = registry.find("price_staleness_seconds")
            .tag("instrument_id", "AAPL")
            .gauge()
        gauge.shouldNotBeNull()
        gauge.value() shouldBeGreaterThanOrEqual 30.0
        gauge.value() shouldBeLessThan 32.0
    }

    test("staleness resets to near zero after a fresh update") {
        val baseTime = Instant.parse("2025-06-01T12:00:00Z")
        val registry = SimpleMeterRegistry()
        val mutableClock = MutableClock(baseTime)
        val tracker = PriceStalenessTracker(registry, mutableClock)

        tracker.recordUpdate(InstrumentId("AAPL"))
        mutableClock.advance(60)

        val gauge = registry.find("price_staleness_seconds")
            .tag("instrument_id", "AAPL")
            .gauge()
        gauge.shouldNotBeNull()
        gauge.value() shouldBeGreaterThanOrEqual 60.0

        tracker.recordUpdate(InstrumentId("AAPL"))
        gauge.value() shouldBeLessThan 2.0
    }
})

/** A mutable clock for testing that can be advanced by a given number of seconds. */
private class MutableClock(
    @Volatile var currentInstant: Instant,
) : Clock() {
    override fun instant(): Instant = currentInstant
    override fun withZone(zone: ZoneId): Clock = this
    override fun getZone(): ZoneId = ZoneOffset.UTC

    fun advance(seconds: Long) {
        currentInstant = currentInstant.plusSeconds(seconds)
    }
}
