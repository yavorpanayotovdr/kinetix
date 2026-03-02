package com.kinetix.correlation.feed

import com.kinetix.common.model.CorrelationMatrix
import com.kinetix.common.model.EstimationMethod
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import java.time.Instant
import kotlin.math.abs
import kotlin.random.Random

private val AS_OF = Instant.parse("2026-02-22T10:00:00Z")
private val LABELS = listOf("A", "B", "C")

private fun seedMatrix(): CorrelationMatrix {
    // 3×3 symmetric matrix with diagonal = 1.0
    // A-B: 0.8, A-C: 0.3, B-C: -0.2
    val values = listOf(
        1.0, 0.8, 0.3,
        0.8, 1.0, -0.2,
        0.3, -0.2, 1.0,
    )
    return CorrelationMatrix(
        labels = LABELS,
        values = values,
        windowDays = 252,
        asOfDate = AS_OF,
        method = EstimationMethod.HISTORICAL,
    )
}

class CorrelationFeedSimulatorTest : FunSpec({

    test("tick produces an updated correlation matrix") {
        val sim = CorrelationFeedSimulator(
            seedMatrix = seedMatrix(),
            random = Random(42),
        )

        val tick = sim.tick(Instant.now())
        tick.labels shouldBe LABELS
        tick.values.size shouldBe 9
    }

    test("diagonal remains 1.0 after many ticks") {
        val sim = CorrelationFeedSimulator(
            seedMatrix = seedMatrix(),
            random = Random(42),
        )

        repeat(10_000) {
            val tick = sim.tick(Instant.now())
            for (i in LABELS.indices) {
                tick.values[i * LABELS.size + i] shouldBe 1.0
            }
        }
    }

    test("matrix remains symmetric after many ticks") {
        val sim = CorrelationFeedSimulator(
            seedMatrix = seedMatrix(),
            random = Random(42),
        )

        repeat(1_000) {
            val tick = sim.tick(Instant.now())
            val n = LABELS.size
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    tick.values[i * n + j] shouldBe tick.values[j * n + i]
                }
            }
        }
    }

    test("correlations stay in [-1, 1] range") {
        val sim = CorrelationFeedSimulator(
            seedMatrix = seedMatrix(),
            random = Random(42),
        )

        repeat(10_000) {
            val tick = sim.tick(Instant.now())
            for (v in tick.values) {
                v shouldBeGreaterThanOrEqual -1.0
                v shouldBeLessThanOrEqual 1.0
            }
        }
    }

    test("correlations mean-revert toward seed values") {
        val sim = CorrelationFeedSimulator(
            seedMatrix = seedMatrix(),
            random = Random(42),
        )

        val seedCorr = 0.8 // A-B correlation
        val observed = mutableListOf<Double>()

        repeat(10_000) {
            val tick = sim.tick(Instant.now())
            observed.add(tick.values[1]) // (0,1) = A-B
        }

        val mean = observed.average()
        val deviation = abs(mean - seedCorr) / abs(seedCorr)
        deviation shouldBeLessThan 0.05
    }

    test("deterministic output with same random seed") {
        fun create() = CorrelationFeedSimulator(
            seedMatrix = seedMatrix(),
            random = Random(99),
        )

        val sim1 = create()
        val sim2 = create()

        val now = Instant.now()
        repeat(10) {
            val t1 = sim1.tick(now)
            val t2 = sim2.tick(now)
            t1.values shouldBe t2.values
        }
    }

    test("labels and window days are preserved") {
        val sim = CorrelationFeedSimulator(
            seedMatrix = seedMatrix(),
            random = Random(42),
        )

        val tick = sim.tick(Instant.now())
        tick.labels shouldBe LABELS
        tick.windowDays shouldBe 252
        tick.method shouldBe EstimationMethod.HISTORICAL
    }
})
