package com.kinetix.volatility.feed

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.VolPoint
import com.kinetix.common.model.VolSurface
import com.kinetix.common.model.VolatilitySource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import kotlin.math.abs
import kotlin.random.Random

private val AS_OF = Instant.parse("2026-02-22T10:00:00Z")

private fun seedSurfaces(): List<VolSurface> {
    val strikePcts = listOf(90, 100, 110)
    val maturities = listOf(30, 90)
    val atmVol = 0.20
    val spotPrice = 100.0

    val points = maturities.flatMap { matDays ->
        strikePcts.map { pct ->
            val strike = (spotPrice * pct / 100.0).toBigDecimal().setScale(2, RoundingMode.HALF_UP)
            val moneyness = (pct - 100).toDouble()
            val skew = when {
                moneyness < 0 -> -moneyness * 0.004
                moneyness > 0 -> moneyness * 0.001
                else -> 0.0
            }
            val vol = (atmVol + skew).coerceAtLeast(0.01)
            VolPoint(
                strike = strike,
                maturityDays = matDays,
                impliedVol = vol.toBigDecimal().setScale(4, RoundingMode.HALF_UP),
            )
        }
    }

    return listOf(
        VolSurface(
            instrumentId = InstrumentId("SPX"),
            asOf = AS_OF,
            points = points,
            source = VolatilitySource.EXCHANGE,
        ),
    )
}

class VolSurfaceFeedSimulatorTest : FunSpec({

    test("tick produces updated vol surfaces for all seeded instruments") {
        val sim = VolSurfaceFeedSimulator(
            seedSurfaces = seedSurfaces(),
            random = Random(42),
        )

        val tick = sim.tick(Instant.now())
        tick shouldHaveSize 1
        tick[0].instrumentId shouldBe InstrumentId("SPX")
    }

    test("tick updates asOf to the provided timestamp") {
        val sim = VolSurfaceFeedSimulator(
            seedSurfaces = seedSurfaces(),
            random = Random(42),
        )

        val now = Instant.parse("2026-03-01T12:00:00Z")
        val tick = sim.tick(now)
        tick[0].asOf shouldBe now
    }

    test("implied vols mean-revert toward seed values") {
        val sim = VolSurfaceFeedSimulator(
            seedSurfaces = seedSurfaces(),
            random = Random(42),
        )

        val seedVol = seedSurfaces()[0].points[0].impliedVol.toDouble()
        val observedVols = mutableListOf<Double>()

        repeat(10_000) {
            val tick = sim.tick(Instant.now())
            observedVols.add(tick[0].points[0].impliedVol.toDouble())
        }

        val meanVol = observedVols.average()
        val deviation = abs(meanVol - seedVol) / seedVol
        deviation shouldBeLessThan 0.05
    }

    test("implied vols remain positive after many ticks") {
        val sim = VolSurfaceFeedSimulator(
            seedSurfaces = seedSurfaces(),
            random = Random(42),
        )

        repeat(10_000) {
            val tick = sim.tick(Instant.now())
            for (surface in tick) {
                for (point in surface.points) {
                    point.impliedVol shouldBeGreaterThan BigDecimal.ZERO
                }
            }
        }
    }

    test("surface structure is preserved across ticks") {
        val sim = VolSurfaceFeedSimulator(
            seedSurfaces = seedSurfaces(),
            random = Random(42),
        )

        val seed = seedSurfaces()[0]
        val tick = sim.tick(Instant.now())

        tick[0].points shouldHaveSize seed.points.size

        tick[0].points.map { it.strike } shouldBe seed.points.map { it.strike }
        tick[0].points.map { it.maturityDays } shouldBe seed.points.map { it.maturityDays }
    }

    test("deterministic output with same random seed") {
        fun create() = VolSurfaceFeedSimulator(
            seedSurfaces = seedSurfaces(),
            random = Random(99),
        )

        val sim1 = create()
        val sim2 = create()

        val now = Instant.now()
        repeat(10) {
            val t1 = sim1.tick(now)
            val t2 = sim2.tick(now)
            for (i in t1[0].points.indices) {
                t1[0].points[i].impliedVol.compareTo(t2[0].points[i].impliedVol) shouldBe 0
            }
        }
    }
})
