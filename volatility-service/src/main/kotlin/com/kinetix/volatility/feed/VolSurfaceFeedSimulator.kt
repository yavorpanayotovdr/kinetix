package com.kinetix.volatility.feed

import com.kinetix.common.model.VolPoint
import com.kinetix.common.model.VolSurface
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

class VolSurfaceFeedSimulator(
    seedSurfaces: List<VolSurface>,
    private val random: Random = Random.Default,
    tickIntervalSeconds: Double = 30.0,
) {
    private val dt = tickIntervalSeconds / SECONDS_PER_YEAR
    private val sqrtDt = sqrt(dt)

    private data class VolPointState(
        val strike: BigDecimal,
        val maturityDays: Int,
        var currentVol: Double,
        val seedVol: Double,
    )

    private data class SurfaceState(
        val seed: VolSurface,
        val points: List<VolPointState>,
    )

    private val surfaceStates: List<SurfaceState> = seedSurfaces.map { surface ->
        SurfaceState(
            seed = surface,
            points = surface.points.map { pt ->
                VolPointState(
                    strike = pt.strike,
                    maturityDays = pt.maturityDays,
                    currentVol = pt.impliedVol.toDouble(),
                    seedVol = pt.impliedVol.toDouble(),
                )
            },
        )
    }

    private fun nextGaussian(): Double {
        var u1: Double
        do { u1 = random.nextDouble() } while (u1 == 0.0)
        val u2 = random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }

    fun tick(timestamp: Instant): List<VolSurface> = surfaceStates.map { state ->
        val points = state.points.map { ps ->
            val z = nextGaussian()
            val newVol = ps.currentVol + MEAN_REVERSION_SPEED * (ps.seedVol - ps.currentVol) * dt +
                VOL_OF_VOL * sqrtDt * z
            ps.currentVol = newVol.coerceAtLeast(MIN_VOL)
            VolPoint(
                strike = ps.strike,
                maturityDays = ps.maturityDays,
                impliedVol = ps.currentVol.toBigDecimal().setScale(4, RoundingMode.HALF_UP),
            )
        }
        state.seed.copy(asOf = timestamp, points = points)
    }

    companion object {
        const val SECONDS_PER_YEAR: Double = 252.0 * 6.5 * 3600.0
        const val MEAN_REVERSION_SPEED: Double = 5.0
        const val VOL_OF_VOL: Double = 0.05
        const val MIN_VOL: Double = 0.01
    }
}
