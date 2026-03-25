package com.kinetix.regulatory.seed

import com.kinetix.regulatory.stress.ScenarioCategory
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
        private val SEED_DATE = Instant.parse("2024-01-01T00:00:00Z")

        private fun regulatoryScenario(
            seedKey: String,
            name: String,
            description: String,
            shocks: String,
        ) = StressScenario(
            id = UUID.nameUUIDFromBytes("seed-scenario-$seedKey".toByteArray()).toString(),
            name = name,
            description = description,
            shocks = shocks,
            status = ScenarioStatus.APPROVED,
            createdBy = "system",
            approvedBy = "system",
            approvedAt = SEED_DATE,
            createdAt = SEED_DATE,
            scenarioType = ScenarioType.HISTORICAL_REPLAY,
            category = ScenarioCategory.REGULATORY_MANDATED,
        )

        private fun internalScenario(
            seedKey: String,
            name: String,
            description: String,
            shocks: String,
        ) = StressScenario(
            id = UUID.nameUUIDFromBytes("seed-scenario-$seedKey".toByteArray()).toString(),
            name = name,
            description = description,
            shocks = shocks,
            status = ScenarioStatus.APPROVED,
            createdBy = "system",
            approvedBy = "system",
            approvedAt = SEED_DATE,
            createdAt = SEED_DATE,
            scenarioType = ScenarioType.HISTORICAL_REPLAY,
            category = ScenarioCategory.INTERNAL_APPROVED,
        )

        val SCENARIOS: List<StressScenario> = listOf(

            // --- REGULATORY_MANDATED scenarios (8) ---

            regulatoryScenario(
                seedKey = "gfc-2008",
                name = "GFC_2008",
                description = "Global Financial Crisis 2008 — Lehman Brothers collapse period (Sep 15–19 2008). " +
                    "Equity markets fell 10–15% in a single week, credit spreads exploded, and FX dislocations " +
                    "against the USD were significant.",
                shocks = """{"equity":-0.10,"fixed_income":-0.03,"fx":-0.04,"commodity":-0.06,"derivative":-0.10}""",
            ),

            regulatoryScenario(
                seedKey = "covid-2020",
                name = "COVID_2020",
                description = "COVID-19 market shock (Feb 24–Mar 20 2020). Fastest bear market in history — " +
                    "equities fell 34% in 33 days, oil collapsed 50%, and rates were cut to zero. " +
                    "Significant flight-to-safety dynamics.",
                shocks = """{"equity":-0.14,"fixed_income":0.01,"fx":-0.05,"commodity":-0.12,"derivative":-0.14}""",
            ),

            regulatoryScenario(
                seedKey = "taper-tantrum-2013",
                name = "TAPER_TANTRUM_2013",
                description = "Taper Tantrum (May–Jun 2013). Ben Bernanke's hint at tapering QE triggered a sharp " +
                    "sell-off in global bonds and EM currencies. 10y Treasury yields jumped 100bps in 7 weeks. " +
                    "EM equities and currencies were the hardest hit.",
                shocks = """{"equity":-0.05,"fixed_income":-0.06,"fx":-0.08,"commodity":-0.03,"derivative":-0.05}""",
            ),

            regulatoryScenario(
                seedKey = "euro-crisis-2011",
                name = "EURO_CRISIS_2011",
                description = "European Sovereign Debt Crisis (Aug–Nov 2011). Italian and Spanish 10y yields " +
                    "surged past 7%, EURUSD fell sharply, and European bank stocks collapsed. ECB emergency " +
                    "intervention was required to stabilise markets.",
                shocks = """{"equity":-0.08,"fixed_income":-0.05,"fx":-0.06,"commodity":-0.04,"derivative":-0.08}""",
            ),

            regulatoryScenario(
                seedKey = "black-monday-1987",
                name = "BLACK_MONDAY_1987",
                description = "Black Monday (Oct 19 1987). The Dow Jones fell 22.6% in a single day — the largest " +
                    "one-day percentage decline in history. Programme trading amplified losses across global " +
                    "equity markets. US Treasuries rallied as a flight-to-quality.",
                shocks = """{"equity":-0.22,"fixed_income":0.02,"fx":-0.03,"commodity":-0.05,"derivative":-0.22}""",
            ),

            regulatoryScenario(
                seedKey = "ltcm-russian-1998",
                name = "LTCM_RUSSIAN_1998",
                description = "LTCM / Russian default crisis (Aug–Sep 1998). Russia defaulted on domestic debt, " +
                    "LTCM required a Fed-orchestrated bailout. Credit spreads widened dramatically, liquidity " +
                    "evaporated, and EM assets were severely repriced.",
                shocks = """{"equity":-0.15,"fixed_income":-0.08,"fx":-0.12,"commodity":-0.07,"derivative":-0.15}""",
            ),

            regulatoryScenario(
                seedKey = "dotcom-2000",
                name = "DOTCOM_2000",
                description = "Dot-com bust (Mar 2000–Mar 2001). Nasdaq fell 78% peak-to-trough over three years. " +
                    "Tech and telecom equities were destroyed while defensive sectors held. Represented a " +
                    "concentrated sector dislocation rather than a systemic crisis.",
                shocks = """{"equity":-0.18,"fixed_income":0.01,"fx":-0.02,"commodity":-0.03,"derivative":-0.18}""",
            ),

            regulatoryScenario(
                seedKey = "sept-11-2001",
                name = "SEPT_11_2001",
                description = "September 11 2001 attacks. NYSE closed for four trading days — longest closure since " +
                    "1933. Markets fell sharply on reopening; aviation, insurance, and defence sectors were most " +
                    "affected. Gold and Treasuries rallied on safe-haven demand.",
                shocks = """{"equity":-0.07,"fixed_income":0.01,"fx":-0.04,"commodity":0.03,"derivative":-0.07}""",
            ),

            // --- INTERNAL_APPROVED scenarios (7) ---

            internalScenario(
                seedKey = "chf-depeg-2015",
                name = "CHF_DEPEG_2015",
                description = "Swiss National Bank CHF floor removal (Jan 15 2015). The SNB abruptly abandoned the " +
                    "1.20 EURCHF floor, causing CHF to appreciate 20–30% instantly. One of the largest single-day " +
                    "FX moves by a G10 currency in modern history.",
                shocks = """{"equity":-0.10,"fixed_income":0.01,"fx":1.28,"commodity":-0.02,"derivative":-0.10}""",
            ),

            internalScenario(
                seedKey = "brexit-2016",
                name = "BREXIT_2016",
                description = "Brexit referendum result (Jun 24 2016). Sterling fell 8% versus the USD overnight, " +
                    "the largest single-day drop in GBP in 30 years. UK equities fell while European equities " +
                    "dropped more moderately. Gilts rallied on Bank of England rate cut expectations.",
                shocks = """{"equity":-0.06,"fixed_income":0.01,"fx":-0.08,"commodity":-0.03,"derivative":-0.06}""",
            ),

            internalScenario(
                seedKey = "volmageddon-2018",
                name = "VOLMAGEDDON_2018",
                description = "Volatility spike (Feb 5–6 2018). The Dow fell 1,175 points in a single session, " +
                    "the VIX spiked from 17 to 37 intraday, and short-volatility products were destroyed. " +
                    "A sharp but brief dislocation driven by systematic unwinds.",
                shocks = """{"equity":-0.10,"fixed_income":-0.01,"fx":-0.02,"commodity":-0.04,"derivative":-0.20}""",
            ),

            internalScenario(
                seedKey = "oil-negative-2020",
                name = "OIL_NEGATIVE_2020",
                description = "WTI crude oil went negative (Apr 20 2020). Front-month WTI futures traded at -37.63 " +
                    "USD per barrel as storage capacity ran out during COVID lockdowns. A unique dislocation in " +
                    "commodity markets with knock-on effects for energy equities and credit.",
                shocks = """{"equity":-0.08,"fixed_income":-0.02,"fx":-0.03,"commodity":-0.55,"derivative":-0.15}""",
            ),

            internalScenario(
                seedKey = "svb-banking-2023",
                name = "SVB_BANKING_2023",
                description = "SVB and regional bank stress (Mar 2023). Silicon Valley Bank failed following a " +
                    "classic bank run, triggering contagion to Signature Bank and Credit Suisse. US short-term " +
                    "rates fell sharply as markets priced in an end to Fed hikes.",
                shocks = """{"equity":-0.08,"fixed_income":0.02,"fx":-0.02,"commodity":-0.03,"derivative":-0.12}""",
            ),

            internalScenario(
                seedKey = "rates-shock-2022",
                name = "RATES_SHOCK_2022",
                description = "Global rates shock (2022). The fastest rate hiking cycle in 40 years — the Fed raised " +
                    "rates 425bps in one year. US 10y yields rose from 1.5% to 4.2%, causing the worst bond " +
                    "market losses in a generation. Equities fell 20%.",
                shocks = """{"equity":-0.20,"fixed_income":-0.15,"fx":-0.06,"commodity":0.05,"derivative":-0.20}""",
            ),

            internalScenario(
                seedKey = "em-contagion",
                name = "EM_CONTAGION",
                description = "Emerging market contagion scenario. Captures a severe EM-specific crisis such as the " +
                    "1997 Asian Financial Crisis or the 2018 EM sell-off. EM currencies depreciate 15–25%, " +
                    "EM equities fall 25%, while developed-market assets see modest contagion.",
                shocks = """{"equity":-0.12,"fixed_income":-0.04,"fx":-0.18,"commodity":-0.06,"derivative":-0.12}""",
            ),
        )
    }
}
