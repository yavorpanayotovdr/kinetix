package com.kinetix.regulatory.seed

import com.kinetix.regulatory.model.FrtbCalculationRecord
import com.kinetix.regulatory.model.RiskClassCharge
import com.kinetix.regulatory.persistence.FrtbCalculationRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class DevDataSeeder(
    private val repository: FrtbCalculationRepository,
) {
    private val log = LoggerFactory.getLogger(DevDataSeeder::class.java)

    suspend fun seed() {
        val existing = repository.findByPortfolioId("equity-growth", limit = 1, offset = 0)
        if (existing.isNotEmpty()) {
            log.info("FRTB data already present, skipping seed")
            return
        }

        log.info("Seeding {} FRTB calculation records", RECORDS.size)

        for (record in RECORDS) {
            repository.save(record)
        }

        log.info("FRTB data seeding complete")
    }

    companion object {
        private val BASE_TIME = Instant.parse("2026-02-22T10:00:00Z")
        private fun daysAgo(d: Long): Instant = BASE_TIME.minus(d, ChronoUnit.DAYS)

        val RECORDS: List<FrtbCalculationRecord> = listOf(
            // equity-growth: 3 records over the week
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-eq-01".toByteArray()).toString(),
                portfolioId = "equity-growth",
                totalSbmCharge = 185_400.0,
                grossJtd = 62_000.0,
                hedgeBenefit = 12_400.0,
                netDrc = 49_600.0,
                exoticNotional = 0.0,
                otherNotional = 0.0,
                totalRrao = 0.0,
                totalCapitalCharge = 235_000.0,
                sbmCharges = listOf(
                    RiskClassCharge("Equity", 142_800.0, 28_600.0, 14_000.0, 185_400.0),
                ),
                calculatedAt = daysAgo(6),
                storedAt = daysAgo(6),
            ),
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-eq-02".toByteArray()).toString(),
                portfolioId = "equity-growth",
                totalSbmCharge = 192_600.0,
                grossJtd = 64_500.0,
                hedgeBenefit = 12_900.0,
                netDrc = 51_600.0,
                exoticNotional = 0.0,
                otherNotional = 0.0,
                totalRrao = 0.0,
                totalCapitalCharge = 244_200.0,
                sbmCharges = listOf(
                    RiskClassCharge("Equity", 148_300.0, 29_800.0, 14_500.0, 192_600.0),
                ),
                calculatedAt = daysAgo(3),
                storedAt = daysAgo(3),
            ),
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-eq-03".toByteArray()).toString(),
                portfolioId = "equity-growth",
                totalSbmCharge = 198_100.0,
                grossJtd = 66_200.0,
                hedgeBenefit = 13_200.0,
                netDrc = 53_000.0,
                exoticNotional = 0.0,
                otherNotional = 0.0,
                totalRrao = 0.0,
                totalCapitalCharge = 251_100.0,
                sbmCharges = listOf(
                    RiskClassCharge("Equity", 152_500.0, 30_600.0, 15_000.0, 198_100.0),
                ),
                calculatedAt = daysAgo(0),
                storedAt = daysAgo(0),
            ),

            // multi-asset: 2 records
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-ma-01".toByteArray()).toString(),
                portfolioId = "multi-asset",
                totalSbmCharge = 310_500.0,
                grossJtd = 95_000.0,
                hedgeBenefit = 28_500.0,
                netDrc = 66_500.0,
                exoticNotional = 812_500.0,
                otherNotional = 0.0,
                totalRrao = 8_125.0,
                totalCapitalCharge = 385_125.0,
                sbmCharges = listOf(
                    RiskClassCharge("Equity", 95_200.0, 18_400.0, 9_200.0, 122_800.0),
                    RiskClassCharge("GIRR", 52_400.0, 8_600.0, 4_200.0, 65_200.0),
                    RiskClassCharge("FX", 38_500.0, 6_200.0, 3_100.0, 47_800.0),
                    RiskClassCharge("Commodity", 42_100.0, 12_600.0, 6_300.0, 61_000.0),
                    RiskClassCharge("CSR", 10_800.0, 2_200.0, 700.0, 13_700.0),
                ),
                calculatedAt = daysAgo(5),
                storedAt = daysAgo(5),
            ),
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-ma-02".toByteArray()).toString(),
                portfolioId = "multi-asset",
                totalSbmCharge = 325_800.0,
                grossJtd = 98_400.0,
                hedgeBenefit = 29_500.0,
                netDrc = 68_900.0,
                exoticNotional = 812_500.0,
                otherNotional = 0.0,
                totalRrao = 8_125.0,
                totalCapitalCharge = 402_825.0,
                sbmCharges = listOf(
                    RiskClassCharge("Equity", 100_100.0, 19_200.0, 9_600.0, 128_900.0),
                    RiskClassCharge("GIRR", 54_800.0, 9_000.0, 4_400.0, 68_200.0),
                    RiskClassCharge("FX", 40_200.0, 6_500.0, 3_200.0, 49_900.0),
                    RiskClassCharge("Commodity", 44_500.0, 13_200.0, 6_600.0, 64_300.0),
                    RiskClassCharge("CSR", 11_500.0, 2_300.0, 700.0, 14_500.0),
                ),
                calculatedAt = daysAgo(1),
                storedAt = daysAgo(1),
            ),

            // derivatives-book: 2 records
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-db-01".toByteArray()).toString(),
                portfolioId = "derivatives-book",
                totalSbmCharge = 278_300.0,
                grossJtd = 45_200.0,
                hedgeBenefit = 13_500.0,
                netDrc = 31_700.0,
                exoticNotional = 4_150_000.0,
                otherNotional = 750_000.0,
                totalRrao = 49_000.0,
                totalCapitalCharge = 359_000.0,
                sbmCharges = listOf(
                    RiskClassCharge("Equity", 165_400.0, 68_200.0, 34_100.0, 267_700.0),
                    RiskClassCharge("CSR", 6_800.0, 2_400.0, 1_400.0, 10_600.0),
                ),
                calculatedAt = daysAgo(4),
                storedAt = daysAgo(4),
            ),
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-db-02".toByteArray()).toString(),
                portfolioId = "derivatives-book",
                totalSbmCharge = 295_600.0,
                grossJtd = 48_100.0,
                hedgeBenefit = 14_400.0,
                netDrc = 33_700.0,
                exoticNotional = 4_150_000.0,
                otherNotional = 750_000.0,
                totalRrao = 49_000.0,
                totalCapitalCharge = 378_300.0,
                sbmCharges = listOf(
                    RiskClassCharge("Equity", 176_200.0, 72_400.0, 36_200.0, 284_800.0),
                    RiskClassCharge("CSR", 7_000.0, 2_500.0, 1_300.0, 10_800.0),
                ),
                calculatedAt = daysAgo(1),
                storedAt = daysAgo(1),
            ),

            // fixed-income: 1 record
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-fi-01".toByteArray()).toString(),
                portfolioId = "fixed-income",
                totalSbmCharge = 42_800.0,
                grossJtd = 18_500.0,
                hedgeBenefit = 5_500.0,
                netDrc = 13_000.0,
                exoticNotional = 0.0,
                otherNotional = 0.0,
                totalRrao = 0.0,
                totalCapitalCharge = 55_800.0,
                sbmCharges = listOf(
                    RiskClassCharge("GIRR", 38_200.0, 3_100.0, 1_500.0, 42_800.0),
                ),
                calculatedAt = daysAgo(2),
                storedAt = daysAgo(2),
            ),
        )
    }
}
