package com.kinetix.correlation.seed

import com.kinetix.common.model.CorrelationMatrix
import com.kinetix.common.model.EstimationMethod
import com.kinetix.correlation.persistence.CorrelationMatrixRepository
import org.slf4j.LoggerFactory
import java.time.Instant

class DevDataSeeder(
    private val correlationMatrixRepository: CorrelationMatrixRepository,
) {
    private val log = LoggerFactory.getLogger(DevDataSeeder::class.java)

    suspend fun seed() {
        val existing = correlationMatrixRepository.findLatest(listOf("AAPL", "MSFT"), WINDOW_DAYS)
        if (existing != null) {
            log.info("Correlation data already present, skipping seed")
            return
        }

        log.info("Seeding correlation matrix for {} instruments", LABELS.size)

        val matrix = CorrelationMatrix(
            labels = LABELS,
            values = CORRELATION_VALUES,
            windowDays = WINDOW_DAYS,
            asOfDate = AS_OF,
            method = EstimationMethod.HISTORICAL,
        )
        correlationMatrixRepository.save(matrix)

        log.info("Correlation matrix seeding complete")
    }

    companion object {
        val AS_OF: Instant = Instant.parse("2026-02-22T10:00:00Z")
        const val WINDOW_DAYS = 252

        val LABELS: List<String> = listOf(
            "AAPL", "AMZN", "BABA", "CL", "DE10Y",
            "EURUSD", "GBPUSD", "GC", "GOOGL", "JPM",
            "META", "MSFT", "NVDA", "SI", "SPX-CALL-5000",
            "SPX-PUT-4500", "TSLA", "US10Y", "US2Y", "US30Y",
            "USDJPY", "VIX-PUT-15",
        )

        private enum class Sector { TECH, FX, FIXED_INCOME, COMMODITY, DERIVATIVE, FINANCE }

        private val SECTOR_MAP: Map<String, Sector> = mapOf(
            "AAPL" to Sector.TECH, "AMZN" to Sector.TECH, "BABA" to Sector.TECH,
            "GOOGL" to Sector.TECH, "META" to Sector.TECH, "MSFT" to Sector.TECH,
            "NVDA" to Sector.TECH, "TSLA" to Sector.TECH,
            "JPM" to Sector.FINANCE,
            "EURUSD" to Sector.FX, "GBPUSD" to Sector.FX, "USDJPY" to Sector.FX,
            "US2Y" to Sector.FIXED_INCOME, "US10Y" to Sector.FIXED_INCOME,
            "US30Y" to Sector.FIXED_INCOME, "DE10Y" to Sector.FIXED_INCOME,
            "GC" to Sector.COMMODITY, "CL" to Sector.COMMODITY, "SI" to Sector.COMMODITY,
            "SPX-PUT-4500" to Sector.DERIVATIVE, "SPX-CALL-5000" to Sector.DERIVATIVE,
            "VIX-PUT-15" to Sector.DERIVATIVE,
        )

        private val SPECIFIC_PAIRS: Map<String, Double> = mapOf(
            "AAPL:MSFT" to 0.82,
            "AAPL:GOOGL" to 0.78,
            "AMZN:GOOGL" to 0.75,
            "META:GOOGL" to 0.73,
            "MSFT:NVDA" to 0.76,
            "AAPL:NVDA" to 0.72,
            "BABA:TSLA" to 0.45,
            "CL:GC" to 0.25,
            "CL:SI" to 0.30,
            "GC:SI" to 0.65,
            "US10Y:US30Y" to 0.95,
            "US2Y:US10Y" to 0.88,
            "US2Y:US30Y" to 0.80,
            "DE10Y:US10Y" to 0.72,
            "DE10Y:US2Y" to 0.60,
            "DE10Y:US30Y" to 0.68,
            "EURUSD:GBPUSD" to 0.70,
            "EURUSD:USDJPY" to -0.40,
            "GBPUSD:USDJPY" to -0.30,
            "SPX-CALL-5000:SPX-PUT-4500" to 0.50,
            "SPX-CALL-5000:VIX-PUT-15" to -0.65,
            "SPX-PUT-4500:VIX-PUT-15" to 0.55,
        )

        private val CROSS_SECTOR_CORRS: Map<Pair<Sector, Sector>, Double> = mapOf(
            (Sector.TECH to Sector.FX) to 0.15,
            (Sector.TECH to Sector.FIXED_INCOME) to -0.20,
            (Sector.TECH to Sector.COMMODITY) to 0.10,
            (Sector.TECH to Sector.DERIVATIVE) to 0.45,
            (Sector.TECH to Sector.FINANCE) to 0.55,
            (Sector.FX to Sector.FIXED_INCOME) to 0.25,
            (Sector.FX to Sector.COMMODITY) to 0.20,
            (Sector.FX to Sector.DERIVATIVE) to 0.05,
            (Sector.FX to Sector.FINANCE) to 0.20,
            (Sector.FIXED_INCOME to Sector.COMMODITY) to -0.15,
            (Sector.FIXED_INCOME to Sector.DERIVATIVE) to -0.10,
            (Sector.FIXED_INCOME to Sector.FINANCE) to 0.30,
            (Sector.COMMODITY to Sector.DERIVATIVE) to 0.08,
            (Sector.COMMODITY to Sector.FINANCE) to 0.15,
            (Sector.DERIVATIVE to Sector.FINANCE) to 0.40,
        )

        // Must be declared after LABELS, SECTOR_MAP, SPECIFIC_PAIRS, and CROSS_SECTOR_CORRS
        val CORRELATION_VALUES: List<Double> = buildCorrelationMatrix()

        private fun buildCorrelationMatrix(): List<Double> {
            val n = LABELS.size
            val matrix = Array(n) { DoubleArray(n) }

            for (i in 0 until n) {
                matrix[i][i] = 1.0
                for (j in i + 1 until n) {
                    val corr = baseCorrelation(LABELS[i], LABELS[j])
                    matrix[i][j] = corr
                    matrix[j][i] = corr
                }
            }

            return matrix.flatMap { it.toList() }
        }

        private fun baseCorrelation(a: String, b: String): Double {
            val sectorA = SECTOR_MAP[a] ?: Sector.TECH
            val sectorB = SECTOR_MAP[b] ?: Sector.TECH

            if (sectorA == sectorB) {
                return when (sectorA) {
                    Sector.TECH -> pairCorrelation(a, b, default = 0.70)
                    Sector.FX -> pairCorrelation(a, b, default = 0.55)
                    Sector.FIXED_INCOME -> pairCorrelation(a, b, default = 0.85)
                    Sector.COMMODITY -> pairCorrelation(a, b, default = 0.35)
                    Sector.DERIVATIVE -> pairCorrelation(a, b, default = 0.60)
                    Sector.FINANCE -> 1.0
                }
            }

            return crossSectorCorrelation(sectorA, sectorB)
        }

        private fun pairCorrelation(a: String, b: String, default: Double): Double {
            val key = if (a < b) "$a:$b" else "$b:$a"
            return SPECIFIC_PAIRS[key] ?: default
        }

        private fun crossSectorCorrelation(a: Sector, b: Sector): Double {
            val key = if (a.ordinal <= b.ordinal) a to b else b to a
            return CROSS_SECTOR_CORRS[key] ?: 0.10
        }
    }
}
