package com.kinetix.rates.feed

import com.kinetix.common.model.CurvePoint
import com.kinetix.common.model.ForwardCurve
import com.kinetix.common.model.RiskFreeRate
import com.kinetix.common.model.Tenor
import com.kinetix.common.model.YieldCurve
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

data class RatesTick(
    val yieldCurves: List<YieldCurve>,
    val riskFreeRates: List<RiskFreeRate>,
    val forwardCurves: List<ForwardCurve>,
)

class RatesFeedSimulator(
    seedYieldCurves: List<YieldCurve>,
    seedRiskFreeRates: List<RiskFreeRate>,
    seedForwardCurves: List<ForwardCurve>,
    private val random: Random = Random.Default,
    tickIntervalSeconds: Double = 30.0,
) {
    private val dt = tickIntervalSeconds / SECONDS_PER_YEAR
    private val sqrtDt = sqrt(dt)

    // Yield curve state: curveId → list of (label, days, currentRate, seedRate)
    private data class TenorState(val label: String, val days: Int, var currentRate: Double, val seedRate: Double)
    private data class YieldCurveState(val seed: YieldCurve, val tenors: List<TenorState>)

    private val yieldCurveStates: List<YieldCurveState> = seedYieldCurves.map { curve ->
        YieldCurveState(
            seed = curve,
            tenors = curve.tenors.map { tenor ->
                TenorState(tenor.label, tenor.days, tenor.rate.toDouble(), tenor.rate.toDouble())
            },
        )
    }

    // Risk-free rate state
    private data class RiskFreeRateState(val seed: RiskFreeRate, var currentRate: Double)

    private val riskFreeRateStates: List<RiskFreeRateState> = seedRiskFreeRates.map {
        RiskFreeRateState(seed = it, currentRate = it.rate)
    }

    // Forward curve state: instrumentId → list of (tenor, currentValue, seedValue)
    private data class CurvePointState(val tenor: String, var currentValue: Double, val seedValue: Double)
    private data class ForwardCurveState(val seed: ForwardCurve, val points: List<CurvePointState>)

    private val forwardCurveStates: List<ForwardCurveState> = seedForwardCurves.map { curve ->
        ForwardCurveState(
            seed = curve,
            points = curve.points.map { pt ->
                CurvePointState(pt.tenor, pt.value, pt.value)
            },
        )
    }

    private fun nextGaussian(): Double {
        var u1: Double
        do { u1 = random.nextDouble() } while (u1 == 0.0)
        val u2 = random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }

    fun tick(timestamp: Instant): RatesTick {
        val yieldCurves = yieldCurveStates.map { state ->
            val tenors = state.tenors.map { ts ->
                val z = nextGaussian()
                val newRate = ts.currentRate + MEAN_REVERSION_SPEED * (ts.seedRate - ts.currentRate) * dt +
                    YIELD_CURVE_VOLATILITY * sqrtDt * z
                ts.currentRate = newRate.coerceAtLeast(0.0)
                Tenor(ts.label, ts.days, BigDecimal(ts.currentRate).setScale(4, RoundingMode.HALF_UP))
            }
            state.seed.copy(asOf = timestamp, tenors = tenors)
        }

        val riskFreeRates = riskFreeRateStates.map { state ->
            val z = nextGaussian()
            val newRate = state.currentRate + MEAN_REVERSION_SPEED * (state.seed.rate - state.currentRate) * dt +
                YIELD_CURVE_VOLATILITY * sqrtDt * z
            state.currentRate = newRate.coerceAtLeast(0.0)
            state.seed.copy(rate = state.currentRate, asOfDate = timestamp)
        }

        val forwardCurves = forwardCurveStates.map { state ->
            val points = state.points.map { ps ->
                val z = nextGaussian()
                val newValue = ps.currentValue + MEAN_REVERSION_SPEED * (ps.seedValue - ps.currentValue) * dt +
                    FORWARD_CURVE_VOLATILITY * ps.currentValue * sqrtDt * z
                ps.currentValue = newValue.coerceAtLeast(0.001)
                CurvePoint(ps.tenor, ps.currentValue)
            }
            state.seed.copy(points = points, asOfDate = timestamp)
        }

        return RatesTick(yieldCurves, riskFreeRates, forwardCurves)
    }

    companion object {
        const val SECONDS_PER_YEAR: Double = 252.0 * 6.5 * 3600.0
        const val MEAN_REVERSION_SPEED: Double = 5.0
        const val YIELD_CURVE_VOLATILITY: Double = 0.01
        const val FORWARD_CURVE_VOLATILITY: Double = 0.005
    }
}
