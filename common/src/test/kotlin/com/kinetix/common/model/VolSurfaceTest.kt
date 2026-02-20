package com.kinetix.common.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant

private val AAPL = InstrumentId("AAPL")
private val NOW = Instant.parse("2025-06-15T00:00:00Z")
private fun vol(value: String) = BigDecimal(value)
private fun strike(value: String) = BigDecimal(value)

// A 3x3 grid: strikes 90, 100, 110 x maturities 30, 90, 365
private fun volSurface(
    instrumentId: InstrumentId = AAPL,
    asOf: Instant = NOW,
    points: List<VolPoint> = listOf(
        VolPoint(strike("90"), 30, vol("0.30")),
        VolPoint(strike("90"), 90, vol("0.28")),
        VolPoint(strike("90"), 365, vol("0.25")),
        VolPoint(strike("100"), 30, vol("0.25")),
        VolPoint(strike("100"), 90, vol("0.23")),
        VolPoint(strike("100"), 365, vol("0.20")),
        VolPoint(strike("110"), 30, vol("0.28")),
        VolPoint(strike("110"), 90, vol("0.26")),
        VolPoint(strike("110"), 365, vol("0.22")),
    ),
) = VolSurface(instrumentId = instrumentId, asOf = asOf, points = points)

class VolSurfaceTest : FunSpec({

    // --- VolPoint tests ---

    test("create VolPoint with valid fields") {
        val point = VolPoint(strike("100"), 30, vol("0.25"))
        point.strike shouldBe strike("100")
        point.maturityDays shouldBe 30
        point.impliedVol shouldBe vol("0.25")
    }

    test("VolPoint with zero strike throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            VolPoint(BigDecimal.ZERO, 30, vol("0.25"))
        }
    }

    test("VolPoint with negative strike throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            VolPoint(BigDecimal("-1"), 30, vol("0.25"))
        }
    }

    test("VolPoint with zero maturity throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            VolPoint(strike("100"), 0, vol("0.25"))
        }
    }

    test("VolPoint with zero implied vol throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            VolPoint(strike("100"), 30, BigDecimal.ZERO)
        }
    }

    test("VolPoint with negative implied vol throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            VolPoint(strike("100"), 30, BigDecimal("-0.1"))
        }
    }

    test("equal VolPoints are equal") {
        VolPoint(strike("100"), 30, vol("0.25")) shouldBe VolPoint(strike("100"), 30, vol("0.25"))
    }

    // --- VolSurface tests ---

    test("create VolSurface with valid fields") {
        val surface = volSurface()
        surface.instrumentId shouldBe AAPL
        surface.asOf shouldBe NOW
        surface.points.size shouldBe 9
    }

    test("VolSurface with empty points throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            volSurface(points = emptyList())
        }
    }

    test("strikes returns distinct sorted strikes") {
        val strikes = volSurface().strikes
        strikes.size shouldBe 3
        strikes[0].compareTo(strike("90")) shouldBe 0
        strikes[1].compareTo(strike("100")) shouldBe 0
        strikes[2].compareTo(strike("110")) shouldBe 0
    }

    test("maturities returns distinct sorted maturities") {
        volSurface().maturities shouldBe listOf(30, 90, 365)
    }

    test("volAt returns exact vol for existing point") {
        volSurface().volAt(strike("100"), 90) shouldBe vol("0.23")
    }

    test("volAt interpolates along strike axis at known maturity") {
        // At maturity=30: strike=90→0.30, strike=100→0.25
        // At strike=95: 0.30 + (0.25-0.30) * (95-90)/(100-90) = 0.30 - 0.025 = 0.275
        val result = volSurface().volAt(strike("95"), 30)
        result.compareTo(vol("0.275")) shouldBe 0
    }

    test("volAt interpolates along maturity axis at known strike") {
        // At strike=100: mat=30→0.25, mat=90→0.23
        // At mat=60: 0.25 + (0.23-0.25) * (60-30)/(90-30) = 0.25 - 0.01 = 0.24
        val result = volSurface().volAt(strike("100"), 60)
        result.compareTo(vol("0.24")) shouldBe 0
    }

    test("volAt performs bilinear interpolation") {
        // Query at strike=95, maturity=60
        // Step 1: interpolate along maturity at strike=90: 0.30 + (0.28-0.30)*(60-30)/(90-30) = 0.30 - 0.01 = 0.29
        // Step 2: interpolate along maturity at strike=100: 0.25 + (0.23-0.25)*(60-30)/(90-30) = 0.25 - 0.01 = 0.24
        // Step 3: interpolate along strike: 0.29 + (0.24-0.29)*(95-90)/(100-90) = 0.29 - 0.025 = 0.265
        val result = volSurface().volAt(strike("95"), 60)
        result.compareTo(vol("0.265")) shouldBe 0
    }

    test("volAt clamps strike below grid minimum") {
        // strike=50 clamps to 90, maturity=30 → 0.30
        volSurface().volAt(strike("50"), 30).compareTo(vol("0.30")) shouldBe 0
    }

    test("volAt clamps strike above grid maximum") {
        // strike=200 clamps to 110, maturity=30 → 0.28
        volSurface().volAt(strike("200"), 30).compareTo(vol("0.28")) shouldBe 0
    }

    test("volAt clamps maturity below grid minimum") {
        // maturity=1 clamps to 30, strike=100 → 0.25
        volSurface().volAt(strike("100"), 1).compareTo(vol("0.25")) shouldBe 0
    }

    test("volAt clamps maturity above grid maximum") {
        // maturity=1000 clamps to 365, strike=100 → 0.20
        volSurface().volAt(strike("100"), 1000).compareTo(vol("0.20")) shouldBe 0
    }

    test("volAt with zero strike throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            volSurface().volAt(BigDecimal.ZERO, 30)
        }
    }

    test("volAt with zero maturity throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            volSurface().volAt(strike("100"), 0)
        }
    }

    test("volAt uses compareTo for BigDecimal strike matching") {
        // 100.0 and 100.00 have different scale but same value
        val result1 = volSurface().volAt(BigDecimal("100.0"), 30)
        val result2 = volSurface().volAt(BigDecimal("100.00"), 30)
        result1.compareTo(result2) shouldBe 0
    }

    test("scaleAll multiplies all implied vols by factor") {
        val scaled = volSurface().scaleAll(BigDecimal("1.5"))
        // Original strike=100, mat=30 vol was 0.25 → 0.375
        val point = scaled.points.first { it.strike.compareTo(strike("100")) == 0 && it.maturityDays == 30 }
        point.impliedVol.compareTo(vol("0.375")) shouldBe 0
    }

    test("scaleAll with factor 1 returns equivalent surface") {
        val original = volSurface()
        val scaled = original.scaleAll(BigDecimal.ONE)
        scaled.points.zip(original.points).forEach { (s, o) ->
            s.impliedVol.compareTo(o.impliedVol) shouldBe 0
        }
    }

    test("scaleAll with zero factor throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            volSurface().scaleAll(BigDecimal.ZERO)
        }
    }

    test("scaleAll returns new VolSurface preserving original") {
        val original = volSurface()
        val scaled = original.scaleAll(BigDecimal("2.0"))
        original.points[0].impliedVol shouldBe vol("0.30")
        scaled.points[0].impliedVol shouldNotBe original.points[0].impliedVol
    }

    test("VolSurface.flat creates surface with uniform vol") {
        val surface = VolSurface.flat(AAPL, NOW, vol("0.20"))
        surface.points.forEach { it.impliedVol shouldBe vol("0.20") }
    }

    test("VolSurface.flat volAt returns same vol for any query") {
        val surface = VolSurface.flat(AAPL, NOW, vol("0.20"))
        surface.volAt(strike("50"), 1).compareTo(vol("0.20")) shouldBe 0
        surface.volAt(strike("150"), 500).compareTo(vol("0.20")) shouldBe 0
        surface.volAt(strike("100"), 30).compareTo(vol("0.20")) shouldBe 0
    }

    test("equal VolSurfaces are equal") {
        volSurface() shouldBe volSurface()
    }
})
