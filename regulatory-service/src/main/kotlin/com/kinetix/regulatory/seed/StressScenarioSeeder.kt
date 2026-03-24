package com.kinetix.regulatory.seed

import com.kinetix.regulatory.stress.ScenarioStatus
import com.kinetix.regulatory.stress.ScenarioType
import com.kinetix.regulatory.stress.StressScenario
import com.kinetix.regulatory.stress.StressScenarioRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class StressScenarioSeeder(
    private val repository: StressScenarioRepository,
) {
    private val log = LoggerFactory.getLogger(StressScenarioSeeder::class.java)

    suspend fun seed() {
        val existing = repository.findAll()
        if (existing.isNotEmpty()) {
            log.info("Stress scenarios already present, skipping seed")
            return
        }

        log.info("Seeding {} historical stress scenarios", SCENARIOS.size)
        for (scenario in SCENARIOS) {
            repository.save(scenario)
        }
        log.info("Historical stress scenario seeding complete")
    }

    companion object {
        private val SCENARIOS: List<StressScenario> = listOf(
            StressScenario(
                id = UUID.nameUUIDFromBytes("seed-scenario-gfc-2008".toByteArray()).toString(),
                name = "GFC_2008",
                description = "Global Financial Crisis 2008 — Lehman Brothers collapse period (Sep 15–19 2008). " +
                    "Equity markets fell 10–15% in a single week, credit spreads exploded, and FX dislocations " +
                    "against the USD were significant.",
                shocks = """
                    {
                        "equity": -0.10,
                        "fixed_income": -0.03,
                        "fx": -0.04,
                        "commodity": -0.06,
                        "derivative": -0.10
                    }
                """.trimIndent(),
                status = ScenarioStatus.APPROVED,
                createdBy = "system",
                approvedBy = "system",
                approvedAt = Instant.parse("2024-01-01T00:00:00Z"),
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                scenarioType = ScenarioType.HISTORICAL_REPLAY,
            ),
            StressScenario(
                id = UUID.nameUUIDFromBytes("seed-scenario-covid-2020".toByteArray()).toString(),
                name = "COVID_2020",
                description = "COVID-19 market shock (Feb 24–Mar 20 2020). Fastest bear market in history — " +
                    "equities fell 34% in 33 days, oil collapsed 50%, and rates were cut to zero. " +
                    "Significant flight-to-safety dynamics.",
                shocks = """
                    {
                        "equity": -0.14,
                        "fixed_income": 0.01,
                        "fx": -0.05,
                        "commodity": -0.12,
                        "derivative": -0.14
                    }
                """.trimIndent(),
                status = ScenarioStatus.APPROVED,
                createdBy = "system",
                approvedBy = "system",
                approvedAt = Instant.parse("2024-01-01T00:00:00Z"),
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                scenarioType = ScenarioType.HISTORICAL_REPLAY,
            ),
            StressScenario(
                id = UUID.nameUUIDFromBytes("seed-scenario-taper-tantrum-2013".toByteArray()).toString(),
                name = "TAPER_TANTRUM_2013",
                description = "Taper Tantrum (May–Jun 2013). Ben Bernanke's hint at tapering QE triggered a sharp " +
                    "sell-off in global bonds and EM currencies. 10y Treasury yields jumped 100bps in 7 weeks. " +
                    "EM equities and currencies were the hardest hit.",
                shocks = """
                    {
                        "equity": -0.05,
                        "fixed_income": -0.06,
                        "fx": -0.08,
                        "commodity": -0.03,
                        "derivative": -0.05
                    }
                """.trimIndent(),
                status = ScenarioStatus.APPROVED,
                createdBy = "system",
                approvedBy = "system",
                approvedAt = Instant.parse("2024-01-01T00:00:00Z"),
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                scenarioType = ScenarioType.HISTORICAL_REPLAY,
            ),
            StressScenario(
                id = UUID.nameUUIDFromBytes("seed-scenario-euro-crisis-2011".toByteArray()).toString(),
                name = "EURO_CRISIS_2011",
                description = "European Sovereign Debt Crisis (Aug–Nov 2011). Italian and Spanish 10y yields " +
                    "surged past 7%, EURUSD fell sharply, and European bank stocks collapsed. ECB emergency " +
                    "intervention was required to stabilise markets.",
                shocks = """
                    {
                        "equity": -0.08,
                        "fixed_income": -0.05,
                        "fx": -0.06,
                        "commodity": -0.04,
                        "derivative": -0.08
                    }
                """.trimIndent(),
                status = ScenarioStatus.APPROVED,
                createdBy = "system",
                approvedBy = "system",
                approvedAt = Instant.parse("2024-01-01T00:00:00Z"),
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                scenarioType = ScenarioType.HISTORICAL_REPLAY,
            ),
        )
    }
}
