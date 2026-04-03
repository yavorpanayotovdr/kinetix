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
            // equity-growth: 3 records over the week (10x scale)
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-eq-01".toByteArray()).toString(),
                bookId = "equity-growth",
                totalSbmCharge = BigDecimal("1854000.0"),
                grossJtd = BigDecimal("620000.0"),
                hedgeBenefit = BigDecimal("124000.0"),
                netDrc = BigDecimal("496000.0"),
                exoticNotional = BigDecimal.ZERO,
                otherNotional = BigDecimal.ZERO,
                totalRrao = BigDecimal.ZERO,
                totalCapitalCharge = BigDecimal("2350000.0"),
                sbmCharges = listOf(
                    RiskClassCharge("Equity", BigDecimal("1428000.0"), BigDecimal("286000.0"), BigDecimal("140000.0"), BigDecimal("1854000.0")),
                ),
                calculatedAt = daysAgo(6),
                storedAt = daysAgo(6),
            ),
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-eq-02".toByteArray()).toString(),
                bookId = "equity-growth",
                totalSbmCharge = BigDecimal("1926000.0"),
                grossJtd = BigDecimal("645000.0"),
                hedgeBenefit = BigDecimal("129000.0"),
                netDrc = BigDecimal("516000.0"),
                exoticNotional = BigDecimal.ZERO,
                otherNotional = BigDecimal.ZERO,
                totalRrao = BigDecimal.ZERO,
                totalCapitalCharge = BigDecimal("2442000.0"),
                sbmCharges = listOf(
                    RiskClassCharge("Equity", BigDecimal("1483000.0"), BigDecimal("298000.0"), BigDecimal("145000.0"), BigDecimal("1926000.0")),
                ),
                calculatedAt = daysAgo(3),
                storedAt = daysAgo(3),
            ),
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-eq-03".toByteArray()).toString(),
                bookId = "equity-growth",
                totalSbmCharge = BigDecimal("1981000.0"),
                grossJtd = BigDecimal("662000.0"),
                hedgeBenefit = BigDecimal("132000.0"),
                netDrc = BigDecimal("530000.0"),
                exoticNotional = BigDecimal.ZERO,
                otherNotional = BigDecimal.ZERO,
                totalRrao = BigDecimal.ZERO,
                totalCapitalCharge = BigDecimal("2511000.0"),
                sbmCharges = listOf(
                    RiskClassCharge("Equity", BigDecimal("1525000.0"), BigDecimal("306000.0"), BigDecimal("150000.0"), BigDecimal("1981000.0")),
                ),
                calculatedAt = daysAgo(0),
                storedAt = daysAgo(0),
            ),

            // multi-asset: 2 records (15x scale)
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-ma-01".toByteArray()).toString(),
                bookId = "multi-asset",
                totalSbmCharge = BigDecimal("4657500.0"),
                grossJtd = BigDecimal("1425000.0"),
                hedgeBenefit = BigDecimal("427500.0"),
                netDrc = BigDecimal("997500.0"),
                exoticNotional = BigDecimal("12187500.0"),
                otherNotional = BigDecimal.ZERO,
                totalRrao = BigDecimal("121875.0"),
                totalCapitalCharge = BigDecimal("5776875.0"),
                sbmCharges = listOf(
                    RiskClassCharge("Equity", BigDecimal("1428000.0"), BigDecimal("276000.0"), BigDecimal("138000.0"), BigDecimal("1842000.0")),
                    RiskClassCharge("GIRR", BigDecimal("786000.0"), BigDecimal("129000.0"), BigDecimal("63000.0"), BigDecimal("978000.0")),
                    RiskClassCharge("FX", BigDecimal("577500.0"), BigDecimal("93000.0"), BigDecimal("46500.0"), BigDecimal("717000.0")),
                    RiskClassCharge("Commodity", BigDecimal("631500.0"), BigDecimal("189000.0"), BigDecimal("94500.0"), BigDecimal("915000.0")),
                    RiskClassCharge("CSR", BigDecimal("162000.0"), BigDecimal("33000.0"), BigDecimal("10500.0"), BigDecimal("205500.0")),
                ),
                calculatedAt = daysAgo(5),
                storedAt = daysAgo(5),
            ),
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-ma-02".toByteArray()).toString(),
                bookId = "multi-asset",
                totalSbmCharge = BigDecimal("4887000.0"),
                grossJtd = BigDecimal("1476000.0"),
                hedgeBenefit = BigDecimal("442500.0"),
                netDrc = BigDecimal("1033500.0"),
                exoticNotional = BigDecimal("12187500.0"),
                otherNotional = BigDecimal.ZERO,
                totalRrao = BigDecimal("121875.0"),
                totalCapitalCharge = BigDecimal("6042375.0"),
                sbmCharges = listOf(
                    RiskClassCharge("Equity", BigDecimal("1501500.0"), BigDecimal("288000.0"), BigDecimal("144000.0"), BigDecimal("1933500.0")),
                    RiskClassCharge("GIRR", BigDecimal("822000.0"), BigDecimal("135000.0"), BigDecimal("66000.0"), BigDecimal("1023000.0")),
                    RiskClassCharge("FX", BigDecimal("603000.0"), BigDecimal("97500.0"), BigDecimal("48000.0"), BigDecimal("748500.0")),
                    RiskClassCharge("Commodity", BigDecimal("667500.0"), BigDecimal("198000.0"), BigDecimal("99000.0"), BigDecimal("964500.0")),
                    RiskClassCharge("CSR", BigDecimal("172500.0"), BigDecimal("34500.0"), BigDecimal("10500.0"), BigDecimal("217500.0")),
                ),
                calculatedAt = daysAgo(1),
                storedAt = daysAgo(1),
            ),

            // derivatives-book: 2 records (15x scale)
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-db-01".toByteArray()).toString(),
                bookId = "derivatives-book",
                totalSbmCharge = BigDecimal("4174500.0"),
                grossJtd = BigDecimal("678000.0"),
                hedgeBenefit = BigDecimal("202500.0"),
                netDrc = BigDecimal("475500.0"),
                exoticNotional = BigDecimal("62250000.0"),
                otherNotional = BigDecimal("11250000.0"),
                totalRrao = BigDecimal("735000.0"),
                totalCapitalCharge = BigDecimal("5385000.0"),
                sbmCharges = listOf(
                    RiskClassCharge("Equity", BigDecimal("2481000.0"), BigDecimal("1023000.0"), BigDecimal("511500.0"), BigDecimal("4015500.0")),
                    RiskClassCharge("CSR", BigDecimal("102000.0"), BigDecimal("36000.0"), BigDecimal("21000.0"), BigDecimal("159000.0")),
                ),
                calculatedAt = daysAgo(4),
                storedAt = daysAgo(4),
            ),
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-db-02".toByteArray()).toString(),
                bookId = "derivatives-book",
                totalSbmCharge = BigDecimal("4434000.0"),
                grossJtd = BigDecimal("721500.0"),
                hedgeBenefit = BigDecimal("216000.0"),
                netDrc = BigDecimal("505500.0"),
                exoticNotional = BigDecimal("62250000.0"),
                otherNotional = BigDecimal("11250000.0"),
                totalRrao = BigDecimal("735000.0"),
                totalCapitalCharge = BigDecimal("5674500.0"),
                sbmCharges = listOf(
                    RiskClassCharge("Equity", BigDecimal("2643000.0"), BigDecimal("1086000.0"), BigDecimal("543000.0"), BigDecimal("4272000.0")),
                    RiskClassCharge("CSR", BigDecimal("105000.0"), BigDecimal("37500.0"), BigDecimal("19500.0"), BigDecimal("162000.0")),
                ),
                calculatedAt = daysAgo(1),
                storedAt = daysAgo(1),
            ),

            // fixed-income: 1 record (6x scale)
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-fi-01".toByteArray()).toString(),
                bookId = "fixed-income",
                totalSbmCharge = BigDecimal("256800.0"),
                grossJtd = BigDecimal("111000.0"),
                hedgeBenefit = BigDecimal("33000.0"),
                netDrc = BigDecimal("78000.0"),
                exoticNotional = BigDecimal.ZERO,
                otherNotional = BigDecimal.ZERO,
                totalRrao = BigDecimal.ZERO,
                totalCapitalCharge = BigDecimal("334800.0"),
                sbmCharges = listOf(
                    RiskClassCharge("GIRR", BigDecimal("229200.0"), BigDecimal("18600.0"), BigDecimal("9000.0"), BigDecimal("256800.0")),
                ),
                calculatedAt = daysAgo(2),
                storedAt = daysAgo(2),
            ),
        )
    }
}
