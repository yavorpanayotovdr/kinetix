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

            // tech-momentum: 2 records (10x scale)
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-tm-01".toByteArray()).toString(),
                bookId = "tech-momentum",
                totalSbmCharge = BigDecimal("1200000.0"),
                grossJtd = BigDecimal("380000.0"),
                hedgeBenefit = BigDecimal("76000.0"),
                netDrc = BigDecimal("304000.0"),
                exoticNotional = BigDecimal.ZERO,
                otherNotional = BigDecimal.ZERO,
                totalRrao = BigDecimal.ZERO,
                totalCapitalCharge = BigDecimal("1504000.0"),
                sbmCharges = listOf(
                    RiskClassCharge("Equity", BigDecimal("1200000.0"), BigDecimal("240000.0"), BigDecimal("120000.0"), BigDecimal("1560000.0")),
                ),
                calculatedAt = daysAgo(5),
                storedAt = daysAgo(5),
            ),
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-tm-02".toByteArray()).toString(),
                bookId = "tech-momentum",
                totalSbmCharge = BigDecimal("1350000.0"),
                grossJtd = BigDecimal("405000.0"),
                hedgeBenefit = BigDecimal.ZERO,
                netDrc = BigDecimal("405000.0"),
                exoticNotional = BigDecimal.ZERO,
                otherNotional = BigDecimal.ZERO,
                totalRrao = BigDecimal.ZERO,
                totalCapitalCharge = BigDecimal("1680000.0"),
                sbmCharges = listOf(
                    RiskClassCharge("Equity", BigDecimal("1350000.0"), BigDecimal("270000.0"), BigDecimal("135000.0"), BigDecimal("1755000.0")),
                ),
                calculatedAt = daysAgo(1),
                storedAt = daysAgo(1),
            ),

            // emerging-markets: 2 records (10x scale)
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-em-01".toByteArray()).toString(),
                bookId = "emerging-markets",
                totalSbmCharge = BigDecimal("890000.0"),
                grossJtd = BigDecimal("280000.0"),
                hedgeBenefit = BigDecimal("56000.0"),
                netDrc = BigDecimal("224000.0"),
                exoticNotional = BigDecimal.ZERO,
                otherNotional = BigDecimal.ZERO,
                totalRrao = BigDecimal.ZERO,
                totalCapitalCharge = BigDecimal("1114000.0"),
                sbmCharges = listOf(
                    RiskClassCharge("Equity", BigDecimal("520000.0"), BigDecimal("104000.0"), BigDecimal("52000.0"), BigDecimal("676000.0")),
                    RiskClassCharge("FX", BigDecimal("370000.0"), BigDecimal("74000.0"), BigDecimal("37000.0"), BigDecimal("481000.0")),
                ),
                calculatedAt = daysAgo(4),
                storedAt = daysAgo(4),
            ),
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-em-02".toByteArray()).toString(),
                bookId = "emerging-markets",
                totalSbmCharge = BigDecimal("950000.0"),
                grossJtd = BigDecimal("300000.0"),
                hedgeBenefit = BigDecimal.ZERO,
                netDrc = BigDecimal("300000.0"),
                exoticNotional = BigDecimal.ZERO,
                otherNotional = BigDecimal.ZERO,
                totalRrao = BigDecimal.ZERO,
                totalCapitalCharge = BigDecimal("1180000.0"),
                sbmCharges = listOf(
                    RiskClassCharge("Equity", BigDecimal("560000.0"), BigDecimal("112000.0"), BigDecimal("56000.0"), BigDecimal("728000.0")),
                    RiskClassCharge("FX", BigDecimal("390000.0"), BigDecimal("78000.0"), BigDecimal("39000.0"), BigDecimal("507000.0")),
                ),
                calculatedAt = daysAgo(1),
                storedAt = daysAgo(1),
            ),

            // macro-hedge: 2 records (15x scale)
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-mh-01".toByteArray()).toString(),
                bookId = "macro-hedge",
                totalSbmCharge = BigDecimal("2800000.0"),
                grossJtd = BigDecimal("450000.0"),
                hedgeBenefit = BigDecimal("90000.0"),
                netDrc = BigDecimal("360000.0"),
                exoticNotional = BigDecimal.ZERO,
                otherNotional = BigDecimal.ZERO,
                totalRrao = BigDecimal.ZERO,
                totalCapitalCharge = BigDecimal("3160000.0"),
                sbmCharges = listOf(
                    RiskClassCharge("FX", BigDecimal("980000.0"), BigDecimal("196000.0"), BigDecimal("98000.0"), BigDecimal("1274000.0")),
                    RiskClassCharge("Commodity", BigDecimal("1400000.0"), BigDecimal("280000.0"), BigDecimal("140000.0"), BigDecimal("1820000.0")),
                    RiskClassCharge("GIRR", BigDecimal("420000.0"), BigDecimal("84000.0"), BigDecimal("42000.0"), BigDecimal("546000.0")),
                ),
                calculatedAt = daysAgo(3),
                storedAt = daysAgo(3),
            ),
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-mh-02".toByteArray()).toString(),
                bookId = "macro-hedge",
                totalSbmCharge = BigDecimal("3100000.0"),
                grossJtd = BigDecimal("480000.0"),
                hedgeBenefit = BigDecimal.ZERO,
                netDrc = BigDecimal("480000.0"),
                exoticNotional = BigDecimal.ZERO,
                otherNotional = BigDecimal.ZERO,
                totalRrao = BigDecimal.ZERO,
                totalCapitalCharge = BigDecimal("3500000.0"),
                sbmCharges = listOf(
                    RiskClassCharge("FX", BigDecimal("1080000.0"), BigDecimal("216000.0"), BigDecimal("108000.0"), BigDecimal("1404000.0")),
                    RiskClassCharge("Commodity", BigDecimal("1560000.0"), BigDecimal("312000.0"), BigDecimal("156000.0"), BigDecimal("2028000.0")),
                    RiskClassCharge("GIRR", BigDecimal("460000.0"), BigDecimal("92000.0"), BigDecimal("46000.0"), BigDecimal("598000.0")),
                ),
                calculatedAt = daysAgo(0),
                storedAt = daysAgo(0),
            ),

            // balanced-income: 2 records (6x scale)
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-bi-01".toByteArray()).toString(),
                bookId = "balanced-income",
                totalSbmCharge = BigDecimal("520000.0"),
                grossJtd = BigDecimal("180000.0"),
                hedgeBenefit = BigDecimal("36000.0"),
                netDrc = BigDecimal("144000.0"),
                exoticNotional = BigDecimal.ZERO,
                otherNotional = BigDecimal.ZERO,
                totalRrao = BigDecimal.ZERO,
                totalCapitalCharge = BigDecimal("664000.0"),
                sbmCharges = listOf(
                    RiskClassCharge("GIRR", BigDecimal("380000.0"), BigDecimal("76000.0"), BigDecimal("38000.0"), BigDecimal("494000.0")),
                    RiskClassCharge("Equity", BigDecimal("140000.0"), BigDecimal("28000.0"), BigDecimal("14000.0"), BigDecimal("182000.0")),
                ),
                calculatedAt = daysAgo(2),
                storedAt = daysAgo(2),
            ),
            FrtbCalculationRecord(
                id = UUID.nameUUIDFromBytes("seed-frtb-bi-02".toByteArray()).toString(),
                bookId = "balanced-income",
                totalSbmCharge = BigDecimal("555000.0"),
                grossJtd = BigDecimal("192000.0"),
                hedgeBenefit = BigDecimal.ZERO,
                netDrc = BigDecimal("192000.0"),
                exoticNotional = BigDecimal.ZERO,
                otherNotional = BigDecimal.ZERO,
                totalRrao = BigDecimal.ZERO,
                totalCapitalCharge = BigDecimal("710000.0"),
                sbmCharges = listOf(
                    RiskClassCharge("GIRR", BigDecimal("405000.0"), BigDecimal("81000.0"), BigDecimal("40500.0"), BigDecimal("526500.0")),
                    RiskClassCharge("Equity", BigDecimal("150000.0"), BigDecimal("30000.0"), BigDecimal("15000.0"), BigDecimal("195000.0")),
                ),
                calculatedAt = daysAgo(0),
                storedAt = daysAgo(0),
            ),
        )
    }
}
