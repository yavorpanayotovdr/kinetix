package com.kinetix.price.metrics

import com.kinetix.common.model.InstrumentId
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class PriceStalenessTracker(
    private val registry: MeterRegistry,
    private val clock: Clock = Clock.systemUTC(),
) {
    internal val lastUpdateTimes = ConcurrentHashMap<String, AtomicReference<Long>>()

    fun recordUpdate(instrumentId: InstrumentId) {
        val id = instrumentId.value
        val ref = lastUpdateTimes.computeIfAbsent(id) { key ->
            val atomicRef = AtomicReference(clock.millis())
            Gauge.builder("price_staleness_seconds") { stalenessSeconds(atomicRef) }
                .tag("instrument_id", key)
                .register(registry)
            atomicRef
        }
        ref.set(clock.millis())
    }

    private fun stalenessSeconds(lastUpdateMillis: AtomicReference<Long>): Double {
        return Duration.ofMillis(clock.millis() - lastUpdateMillis.get()).toSeconds().toDouble()
    }
}
