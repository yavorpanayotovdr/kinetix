package com.kinetix.price.feed

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.Money
import com.kinetix.common.model.PricePoint
import com.kinetix.common.model.PriceSource
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.Currency
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

class PriceFeedSimulator(
    seeds: List<InstrumentSeed>,
    tickIntervalSeconds: Double = 1.0,
    private val random: Random = Random.Default,
) {
    private val currentPrices: MutableMap<InstrumentId, BigDecimal> = seeds.associate {
        it.instrumentId to it.initialPrice
    }.toMutableMap()

    private val currencies: Map<InstrumentId, Currency> = seeds.associate {
        it.instrumentId to it.currency
    }

    private val assetClasses: Map<InstrumentId, AssetClass> = seeds.associate {
        it.instrumentId to it.assetClass
    }

    private val meanPrices: Map<InstrumentId, BigDecimal> = seeds.associate {
        it.instrumentId to it.initialPrice
    }

    private val scales: Map<InstrumentId, Int> = seeds.associate {
        it.instrumentId to it.initialPrice.scale()
    }

    private val dt: Double = tickIntervalSeconds / SECONDS_PER_YEAR
    private val sqrtDt: Double = sqrt(dt)

    private fun nextGaussian(): Double {
        var u1: Double
        do { u1 = random.nextDouble() } while (u1 == 0.0)
        val u2 = random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }

    fun tick(timestamp: Instant, source: PriceSource): List<PricePoint> =
        currentPrices.keys.map { instrumentId ->
            val currentPrice = currentPrices.getValue(instrumentId)
            val assetClass = assetClasses.getValue(instrumentId)
            val sigma = ANNUAL_VOLATILITY.getValue(assetClass)
            val scale = scales.getValue(instrumentId)
            val z = nextGaussian()

            val newPriceDouble = when (assetClass) {
                AssetClass.FX, AssetClass.FIXED_INCOME -> {
                    val mean = meanPrices.getValue(instrumentId).toDouble()
                    val current = currentPrice.toDouble()
                    current + MEAN_REVERSION_SPEED * (mean - current) * dt + sigma * current * sqrtDt * z
                }
                else -> {
                    val current = currentPrice.toDouble()
                    current * exp(-0.5 * sigma * sigma * dt + sigma * sqrtDt * z)
                }
            }

            val newPrice = BigDecimal(newPriceDouble.coerceAtLeast(0.01))
                .setScale(scale, RoundingMode.HALF_UP)

            currentPrices[instrumentId] = newPrice

            PricePoint(
                instrumentId = instrumentId,
                price = Money(newPrice, currencies.getValue(instrumentId)),
                timestamp = timestamp,
                source = source,
            )
        }

    companion object {
        val ANNUAL_VOLATILITY: Map<AssetClass, Double> = mapOf(
            AssetClass.EQUITY to 0.20,
            AssetClass.FX to 0.10,
            AssetClass.FIXED_INCOME to 0.05,
            AssetClass.COMMODITY to 0.25,
            AssetClass.DERIVATIVE to 0.35,
        )
        const val SECONDS_PER_YEAR: Double = 252.0 * 6.5 * 3600.0
        const val MEAN_REVERSION_SPEED: Double = 5.0
    }
}
