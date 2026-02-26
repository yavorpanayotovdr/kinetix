package com.kinetix.risk.simulation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.server.config.MapApplicationConfig

class SimulationDelaysTest : FunSpec({

    test("returns null when delays are disabled") {
        val config = MapApplicationConfig(
            "simulation.delays.enabled" to "false",
            "simulation.delays.fetchPositionsMs.min" to "2000",
            "simulation.delays.fetchPositionsMs.max" to "5000",
            "simulation.delays.discoverDependenciesMs.min" to "1000",
            "simulation.delays.discoverDependenciesMs.max" to "3000",
            "simulation.delays.fetchMarketDataPerCallMs.min" to "200",
            "simulation.delays.fetchMarketDataPerCallMs.max" to "1500",
            "simulation.delays.calculateVaRMs.min" to "4000",
            "simulation.delays.calculateVaRMs.max" to "10000",
        )

        SimulationDelays.from(config).shouldBeNull()
    }

    test("returns null when simulation section is missing") {
        val config = MapApplicationConfig()

        SimulationDelays.from(config).shouldBeNull()
    }

    test("returns delays when explicitly enabled") {
        val config = MapApplicationConfig(
            "simulation.delays.enabled" to "true",
            "simulation.delays.fetchPositionsMs.min" to "2000",
            "simulation.delays.fetchPositionsMs.max" to "5000",
            "simulation.delays.discoverDependenciesMs.min" to "1000",
            "simulation.delays.discoverDependenciesMs.max" to "3000",
            "simulation.delays.fetchMarketDataPerCallMs.min" to "200",
            "simulation.delays.fetchMarketDataPerCallMs.max" to "1500",
            "simulation.delays.calculateVaRMs.min" to "4000",
            "simulation.delays.calculateVaRMs.max" to "10000",
        )

        val delays = SimulationDelays.from(config)

        delays.shouldNotBeNull()
        delays.fetchPositionsMs shouldBe 2000L..5000L
        delays.discoverDependenciesMs shouldBe 1000L..3000L
        delays.fetchMarketDataPerCallMs shouldBe 200L..1500L
        delays.calculateVaRMs shouldBe 4000L..10000L
    }

    test("production application.conf has delays disabled by default") {
        val hocon = com.typesafe.config.ConfigFactory.parseResources("application.conf")
            .resolve(com.typesafe.config.ConfigResolveOptions.defaults().setAllowUnresolved(true))
        val config = io.ktor.server.config.HoconApplicationConfig(hocon)

        SimulationDelays.from(config).shouldBeNull()
    }
})
