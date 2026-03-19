package com.kinetix.regulatory.seed

import com.kinetix.regulatory.model.FrtbCalculationRecord
import com.kinetix.regulatory.model.RiskClassCharge
import com.kinetix.regulatory.persistence.FrtbCalculationRepository
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class DevDataSeeder(
    private val repository: FrtbCalculationRepository,
) {
    private val log = LoggerFactory.getLogger(DevDataSeeder::class.java)

    suspend fun seed() {
        val existing = repository.findByBookId("equity-growth", limit = 1, offset = 0, from = Instant.EPOCH)
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
                bookId = "equity-growth",
                totalSbmCharge = BigDecimal("185400.0"),
                grossJtd = BigDecimal("62000.0"),
                hedgeBenefit = BigDecimal("12400.0"),
                netDrc = BigDecimal("49600.0"),
                exoticNotional = BigDecimal.ZERO,
                otherNotional = BigDecimal.ZERO,
                totalRrao = BigDecimal.ZERO,
                totalCapitalCharge = BigDecimal("235000.0"),
                sbmCharges = listOf(
                    RiskClassCharge("Equity", BigDecimal("142800.0"), BigDecimal("28600.0"), BigDecimal("14000.0"), BigDecimal("185400.0")),
                ),
                calculatedAt = daysAgo(6),
                storedAt = daysAgo(6),
            ),
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-eq-02".toByteArray()).toString(),
                bookId = "equity-growth",
                totalSbmCharge = BigDecimal("192600.0"),
                grossJtd = BigDecimal("64500.0"),
                hedgeBenefit = BigDecimal("12900.0"),
                netDrc = BigDecimal("51600.0"),
                exoticNotional = BigDecimal.ZERO,
                otherNotional = BigDecimal.ZERO,
                totalRrao = BigDecimal.ZERO,
                totalCapitalCharge = BigDecimal("244200.0"),
                sbmCharges = listOf(
                    RiskClassCharge("Equity", BigDecimal("148300.0"), BigDecimal("29800.0"), BigDecimal("14500.0"), BigDecimal("192600.0")),
                ),
                calculatedAt = daysAgo(3),
                storedAt = daysAgo(3),
            ),
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-eq-03".toByteArray()).toString(),
                bookId = "equity-growth",
                totalSbmCharge = BigDecimal("198100.0"),
                grossJtd = BigDecimal("66200.0"),
                hedgeBenefit = BigDecimal("13200.0"),
                netDrc = BigDecimal("53000.0"),
                exoticNotional = BigDecimal.ZERO,
                otherNotional = BigDecimal.ZERO,
                totalRrao = BigDecimal.ZERO,
                totalCapitalCharge = BigDecimal("251100.0"),
                sbmCharges = listOf(
                    RiskClassCharge("Equity", BigDecimal("152500.0"), BigDecimal("30600.0"), BigDecimal("15000.0"), BigDecimal("198100.0")),
                ),
                calculatedAt = daysAgo(0),
                storedAt = daysAgo(0),
            ),

            // multi-asset: 2 records
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-ma-01".toByteArray()).toString(),
                bookId = "multi-asset",
                totalSbmCharge = BigDecimal("310500.0"),
                grossJtd = BigDecimal("95000.0"),
                hedgeBenefit = BigDecimal("28500.0"),
                netDrc = BigDecimal("66500.0"),
                exoticNotional = BigDecimal("812500.0"),
                otherNotional = BigDecimal.ZERO,
                totalRrao = BigDecimal("8125.0"),
                totalCapitalCharge = BigDecimal("385125.0"),
                sbmCharges = listOf(
                    RiskClassCharge("Equity", BigDecimal("95200.0"), BigDecimal("18400.0"), BigDecimal("9200.0"), BigDecimal("122800.0")),
                    RiskClassCharge("GIRR", BigDecimal("52400.0"), BigDecimal("8600.0"), BigDecimal("4200.0"), BigDecimal("65200.0")),
                    RiskClassCharge("FX", BigDecimal("38500.0"), BigDecimal("6200.0"), BigDecimal("3100.0"), BigDecimal("47800.0")),
                    RiskClassCharge("Commodity", BigDecimal("42100.0"), BigDecimal("12600.0"), BigDecimal("6300.0"), BigDecimal("61000.0")),
                    RiskClassCharge("CSR", BigDecimal("10800.0"), BigDecimal("2200.0"), BigDecimal("700.0"), BigDecimal("13700.0")),
                ),
                calculatedAt = daysAgo(5),
                storedAt = daysAgo(5),
            ),
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-ma-02".toByteArray()).toString(),
                bookId = "multi-asset",
                totalSbmCharge = BigDecimal("325800.0"),
                grossJtd = BigDecimal("98400.0"),
                hedgeBenefit = BigDecimal("29500.0"),
                netDrc = BigDecimal("68900.0"),
                exoticNotional = BigDecimal("812500.0"),
                otherNotional = BigDecimal.ZERO,
                totalRrao = BigDecimal("8125.0"),
                totalCapitalCharge = BigDecimal("402825.0"),
                sbmCharges = listOf(
                    RiskClassCharge("Equity", BigDecimal("100100.0"), BigDecimal("19200.0"), BigDecimal("9600.0"), BigDecimal("128900.0")),
                    RiskClassCharge("GIRR", BigDecimal("54800.0"), BigDecimal("9000.0"), BigDecimal("4400.0"), BigDecimal("68200.0")),
                    RiskClassCharge("FX", BigDecimal("40200.0"), BigDecimal("6500.0"), BigDecimal("3200.0"), BigDecimal("49900.0")),
                    RiskClassCharge("Commodity", BigDecimal("44500.0"), BigDecimal("13200.0"), BigDecimal("6600.0"), BigDecimal("64300.0")),
                    RiskClassCharge("CSR", BigDecimal("11500.0"), BigDecimal("2300.0"), BigDecimal("700.0"), BigDecimal("14500.0")),
                ),
                calculatedAt = daysAgo(1),
                storedAt = daysAgo(1),
            ),

            // derivatives-book: 2 records
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-db-01".toByteArray()).toString(),
                bookId = "derivatives-book",
                totalSbmCharge = BigDecimal("278300.0"),
                grossJtd = BigDecimal("45200.0"),
                hedgeBenefit = BigDecimal("13500.0"),
                netDrc = BigDecimal("31700.0"),
                exoticNotional = BigDecimal("4150000.0"),
                otherNotional = BigDecimal("750000.0"),
                totalRrao = BigDecimal("49000.0"),
                totalCapitalCharge = BigDecimal("359000.0"),
                sbmCharges = listOf(
                    RiskClassCharge("Equity", BigDecimal("165400.0"), BigDecimal("68200.0"), BigDecimal("34100.0"), BigDecimal("267700.0")),
                    RiskClassCharge("CSR", BigDecimal("6800.0"), BigDecimal("2400.0"), BigDecimal("1400.0"), BigDecimal("10600.0")),
                ),
                calculatedAt = daysAgo(4),
                storedAt = daysAgo(4),
            ),
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-db-02".toByteArray()).toString(),
                bookId = "derivatives-book",
                totalSbmCharge = BigDecimal("295600.0"),
                grossJtd = BigDecimal("48100.0"),
                hedgeBenefit = BigDecimal("14400.0"),
                netDrc = BigDecimal("33700.0"),
                exoticNotional = BigDecimal("4150000.0"),
                otherNotional = BigDecimal("750000.0"),
                totalRrao = BigDecimal("49000.0"),
                totalCapitalCharge = BigDecimal("378300.0"),
                sbmCharges = listOf(
                    RiskClassCharge("Equity", BigDecimal("176200.0"), BigDecimal("72400.0"), BigDecimal("36200.0"), BigDecimal("284800.0")),
                    RiskClassCharge("CSR", BigDecimal("7000.0"), BigDecimal("2500.0"), BigDecimal("1300.0"), BigDecimal("10800.0")),
                ),
                calculatedAt = daysAgo(1),
                storedAt = daysAgo(1),
            ),

            // fixed-income: 1 record
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-fi-01".toByteArray()).toString(),
                bookId = "fixed-income",
                totalSbmCharge = BigDecimal("42800.0"),
                grossJtd = BigDecimal("18500.0"),
                hedgeBenefit = BigDecimal("5500.0"),
                netDrc = BigDecimal("13000.0"),
                exoticNotional = BigDecimal.ZERO,
                otherNotional = BigDecimal.ZERO,
                totalRrao = BigDecimal.ZERO,
                totalCapitalCharge = BigDecimal("55800.0"),
                sbmCharges = listOf(
                    RiskClassCharge("GIRR", BigDecimal("38200.0"), BigDecimal("3100.0"), BigDecimal("1500.0"), BigDecimal("42800.0")),
                ),
                calculatedAt = daysAgo(2),
                storedAt = daysAgo(2),
            ),
        )
    }
}
