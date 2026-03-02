package com.kinetix.correlation.feed

import com.kinetix.common.model.CorrelationMatrix
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

class CorrelationFeedSimulator(
    private val seedMatrix: CorrelationMatrix,
    private val random: Random = Random.Default,
    tickIntervalSeconds: Double = 60.0,
) {
    private val n = seedMatrix.labels.size
    private val dt = tickIntervalSeconds / SECONDS_PER_YEAR
    private val sqrtDt = sqrt(dt)

    private val seedValues: DoubleArray = seedMatrix.values.toDoubleArray()
    private val currentValues: DoubleArray = seedValues.copyOf()

    private fun nextGaussian(): Double {
        var u1: Double
        do { u1 = random.nextDouble() } while (u1 == 0.0)
        val u2 = random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }

    fun tick(timestamp: Instant): CorrelationMatrix {
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val idx = i * n + j
                val idxSym = j * n + i
                val z = nextGaussian()
                val newCorr = currentValues[idx] +
                    MEAN_REVERSION_SPEED * (seedValues[idx] - currentValues[idx]) * dt +
                    VOLATILITY * sqrtDt * z
                val clamped = newCorr.coerceIn(-1.0, 1.0)
                currentValues[idx] = clamped
                currentValues[idxSym] = clamped
            }
        }

        return seedMatrix.copy(
            values = currentValues.toList(),
            asOfDate = timestamp,
        )
    }

    companion object {
        const val SECONDS_PER_YEAR: Double = 252.0 * 6.5 * 3600.0
        const val MEAN_REVERSION_SPEED: Double = 5.0
        const val VOLATILITY: Double = 0.02
    }
}
