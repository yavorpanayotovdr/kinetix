package com.kinetix.common.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")
private val NOW = Instant.parse("2025-06-15T00:00:00Z")
private fun rate(value: String) = BigDecimal(value)

private fun yieldCurve(
    currency: Currency = USD,
    asOf: Instant = NOW,
    tenors: List<Tenor> = listOf(
        Tenor.oneMonth(rate("0.0400")),
        Tenor.threeMonths(rate("0.0425")),
        Tenor.oneYear(rate("0.0450")),
        Tenor.fiveYears(rate("0.0475")),
        Tenor.tenYears(rate("0.0500")),
        Tenor.thirtyYears(rate("0.0525")),
    ),
) = YieldCurve(currency = currency, asOf = asOf, tenors = tenors)

class YieldCurveTest : FunSpec({

    test("create YieldCurve with valid fields") {
        val curve = yieldCurve()
        curve.currency shouldBe USD
        curve.asOf shouldBe NOW
        curve.tenors.size shouldBe 6
    }

    test("YieldCurve with empty tenors throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            yieldCurve(tenors = emptyList())
        }
    }

    test("YieldCurve with unsorted tenors throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            yieldCurve(
                tenors = listOf(
                    Tenor.oneYear(rate("0.05")),
                    Tenor.oneMonth(rate("0.04")),
                ),
            )
        }
    }

    test("YieldCurve with duplicate maturity days throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            yieldCurve(
                tenors = listOf(
                    Tenor("A", 365, rate("0.04")),
                    Tenor("B", 365, rate("0.05")),
                ),
            )
        }
    }

    test("shortRate returns first tenor rate") {
        yieldCurve().shortRate shouldBe rate("0.0400")
    }

    test("longRate returns last tenor rate") {
        yieldCurve().longRate shouldBe rate("0.0525")
    }

    test("rateAt returns exact rate for existing tenor") {
        yieldCurve().rateAt(365) shouldBe rate("0.0450")
    }

    test("rateAt interpolates between tenors") {
        // Between 3M (90d, 0.0425) and 1Y (365d, 0.0450)
        // At 180d: 0.0425 + (0.0025 * 90/275)
        val result = yieldCurve().rateAt(180)
        // Linear interpolation: lower=90d/0.0425, upper=365d/0.0450
        // offset = 180-90 = 90, range = 365-90 = 275
        // result = 0.0425 + 0.0025 * (90/275)
        val expected = rate("0.0425") + (rate("0.0025") * BigDecimal("90")).divide(BigDecimal("275"), java.math.MathContext.DECIMAL128)
        result.compareTo(expected) shouldBe 0
    }

    test("rateAt extrapolates flat below shortest tenor") {
        yieldCurve().rateAt(1) shouldBe rate("0.0400")
    }

    test("rateAt extrapolates flat above longest tenor") {
        yieldCurve().rateAt(20000) shouldBe rate("0.0525")
    }

    test("rateAt with zero days throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            yieldCurve().rateAt(0)
        }
    }

    test("rateAt with negative days throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            yieldCurve().rateAt(-1)
        }
    }

    test("rateAt midpoint interpolation is exact") {
        val curve = YieldCurve(
            currency = USD,
            asOf = NOW,
            tenors = listOf(
                Tenor("A", 100, rate("0.04")),
                Tenor("B", 200, rate("0.06")),
            ),
        )
        // Midpoint at 150: (0.04 + 0.06) / 2 = 0.05
        curve.rateAt(150).compareTo(rate("0.05")) shouldBe 0
    }

    test("shift moves all rates by given basis points") {
        val curve = yieldCurve(
            tenors = listOf(
                Tenor.oneMonth(rate("0.0400")),
                Tenor.oneYear(rate("0.0500")),
            ),
        )
        val shifted = curve.shift(BigDecimal("100")) // +100bp = +0.01
        shifted.tenors[0].rate.compareTo(rate("0.0500")) shouldBe 0
        shifted.tenors[1].rate.compareTo(rate("0.0600")) shouldBe 0
    }

    test("shift with negative basis points decreases rates") {
        val curve = yieldCurve(
            tenors = listOf(
                Tenor.oneMonth(rate("0.0400")),
                Tenor.oneYear(rate("0.0500")),
            ),
        )
        val shifted = curve.shift(BigDecimal("-50")) // -50bp = -0.005
        shifted.tenors[0].rate.compareTo(rate("0.0350")) shouldBe 0
        shifted.tenors[1].rate.compareTo(rate("0.0450")) shouldBe 0
    }

    test("shift returns new YieldCurve preserving original") {
        val original = yieldCurve()
        val shifted = original.shift(BigDecimal("100"))
        original.tenors[0].rate shouldBe rate("0.0400")
        shifted.tenors[0].rate shouldNotBe original.tenors[0].rate
    }

    test("YieldCurve.flat creates curve with uniform rate") {
        val curve = YieldCurve.flat(USD, NOW, rate("0.05"))
        curve.tenors.forEach { it.rate shouldBe rate("0.05") }
        curve.currency shouldBe USD
    }

    test("equal YieldCurves are equal") {
        yieldCurve() shouldBe yieldCurve()
    }
})
