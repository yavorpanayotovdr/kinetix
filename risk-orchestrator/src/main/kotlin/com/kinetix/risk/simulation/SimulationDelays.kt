package com.kinetix.risk.simulation

import io.ktor.server.config.ApplicationConfig

data class SimulationDelays(
    val fetchPositionsMs: LongRange,
    val discoverDependenciesMs: LongRange,
    val fetchMarketDataPerCallMs: LongRange,
    val calculateVaRMs: LongRange,
) {
    companion object {
        fun from(config: ApplicationConfig): SimulationDelays? {
            val delays = config.configOrNull("simulation.delays") ?: return null
            val enabled = delays.propertyOrNull("enabled")?.getString()?.toBoolean() ?: false
            if (!enabled) return null

            return SimulationDelays(
                fetchPositionsMs = delays.longRange("fetchPositionsMs"),
                discoverDependenciesMs = delays.longRange("discoverDependenciesMs"),
                fetchMarketDataPerCallMs = delays.longRange("fetchMarketDataPerCallMs"),
                calculateVaRMs = delays.longRange("calculateVaRMs"),
            )
        }

        private fun ApplicationConfig.longRange(key: String): LongRange {
            val sub = config(key)
            val min = sub.property("min").getString().toLong()
            val max = sub.property("max").getString().toLong()
            return min..max
        }

        private fun ApplicationConfig.configOrNull(path: String): ApplicationConfig? =
            try {
                config(path)
            } catch (_: Exception) {
                null
            }
    }
}
