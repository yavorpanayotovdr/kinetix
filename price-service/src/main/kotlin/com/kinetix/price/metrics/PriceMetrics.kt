package com.kinetix.price.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration

class PriceMetrics(registry: MeterRegistry) {
    private val updatesCounter: Counter = Counter.builder("price_updates")
        .description("Total number of price updates ingested")
        .register(registry)

    private val feedLatencyTimer: Timer = Timer.builder("price_feed_latency_seconds")
        .description("Latency of price feed ingestion")
        .publishPercentileHistogram()
        .register(registry)

    fun recordUpdate() {
        updatesCounter.increment()
    }

    fun recordLatency(duration: Duration) {
        feedLatencyTimer.record(duration)
    }
}
