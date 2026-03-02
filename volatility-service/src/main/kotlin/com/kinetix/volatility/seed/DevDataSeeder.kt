package com.kinetix.volatility.seed

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.VolPoint
import com.kinetix.common.model.VolSurface
import com.kinetix.common.model.VolatilitySource
import com.kinetix.volatility.persistence.VolSurfaceRepository
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

class DevDataSeeder(
    private val volSurfaceRepository: VolSurfaceRepository,
) {
    private val log = LoggerFactory.getLogger(DevDataSeeder::class.java)

    suspend fun seed() {
        val existing = volSurfaceRepository.findLatest(InstrumentId("SPX"))
        if (existing != null) {
            log.info("Volatility data already present, skipping seed")
            return
        }

        log.info("Seeding volatility surfaces for {} underlyings", SURFACE_CONFIGS.size)

        for ((underlying, config) in SURFACE_CONFIGS) {
            seedSurface(underlying, config)
        }

        log.info("Volatility surface seeding complete")
    }

    private suspend fun seedSurface(underlying: String, config: SurfaceConfig) {
        val points = mutableListOf<VolPoint>()
        for (maturityDays in MATURITY_DAYS) {
            for (strikePercent in STRIKE_PERCENTS) {
                val strike = (config.spotPrice * strikePercent / 100.0).toBigDecimal()
                    .setScale(2, RoundingMode.HALF_UP)
                val impliedVol = computeImpliedVol(config.atmVol, strikePercent, maturityDays)
                points.add(
                    VolPoint(
                        strike = strike,
                        maturityDays = maturityDays,
                        impliedVol = impliedVol,
                    ),
                )
            }
        }

        val surface = VolSurface(
            instrumentId = InstrumentId(underlying),
            asOf = AS_OF,
            points = points,
            source = VolatilitySource.EXCHANGE,
        )
        volSurfaceRepository.save(surface)
    }

    internal data class SurfaceConfig(
        val spotPrice: Double,
        val atmVol: Double,
    )

    companion object {
        val AS_OF: Instant = Instant.parse("2026-02-22T10:00:00Z")

        val STRIKE_PERCENTS = listOf(80, 90, 95, 100, 105, 110, 120)
        val MATURITY_DAYS = listOf(30, 60, 90, 180, 365)

        internal val SURFACE_CONFIGS: Map<String, SurfaceConfig> = mapOf(
            "SPX" to SurfaceConfig(spotPrice = 5000.0, atmVol = 0.18),
            "VIX" to SurfaceConfig(spotPrice = 15.0, atmVol = 0.80),
            "NVDA" to SurfaceConfig(spotPrice = 892.50, atmVol = 0.45),
            "TSLA" to SurfaceConfig(spotPrice = 242.15, atmVol = 0.55),
        )

        internal fun computeImpliedVol(atmVol: Double, strikePercent: Int, maturityDays: Int): BigDecimal {
            val moneyness = (strikePercent - 100).toDouble()
            val skew = when {
                moneyness < 0 -> -moneyness * 0.004
                moneyness > 0 -> moneyness * 0.001
                else -> 0.0
            }
            val termAdjust = (maturityDays - 90).toDouble() / 365.0 * 0.02
            val vol = (atmVol + skew - termAdjust).coerceAtLeast(0.01)
            return vol.toBigDecimal().setScale(4, RoundingMode.HALF_UP)
        }
    }
}
