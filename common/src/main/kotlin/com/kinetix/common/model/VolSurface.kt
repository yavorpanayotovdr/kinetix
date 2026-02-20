package com.kinetix.common.model

import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant

data class VolSurface(
    val instrumentId: InstrumentId,
    val asOf: Instant,
    val points: List<VolPoint>,
) {
    init {
        require(points.isNotEmpty()) { "VolSurface must have at least one point" }
    }

    val strikes: List<BigDecimal>
        get() = points.map { it.strike }.distinctBy { it.stripTrailingZeros() }.sortedWith(Comparator { a, b -> a.compareTo(b) })

    val maturities: List<Int>
        get() = points.map { it.maturityDays }.distinct().sorted()

    fun volAt(strike: BigDecimal, maturityDays: Int): BigDecimal {
        require(strike > BigDecimal.ZERO) { "Strike must be positive, was $strike" }
        require(maturityDays > 0) { "Maturity days must be positive, was $maturityDays" }

        points.find { it.strike.compareTo(strike) == 0 && it.maturityDays == maturityDays }
            ?.let { return it.impliedVol }

        val sortedMaturities = maturities
        val sortedStrikes = strikes

        val clampedMaturity = maturityDays.coerceIn(sortedMaturities.first(), sortedMaturities.last())
        val clampedStrike = clampStrike(strike, sortedStrikes)

        val matLower = sortedMaturities.last { it <= clampedMaturity }
        val matUpper = sortedMaturities.first { it >= clampedMaturity }

        val strikeLower = sortedStrikes.last { it.compareTo(clampedStrike) <= 0 }
        val strikeUpper = sortedStrikes.first { it.compareTo(clampedStrike) >= 0 }

        fun volLookup(s: BigDecimal, m: Int): BigDecimal =
            points.first { it.strike.compareTo(s) == 0 && it.maturityDays == m }.impliedVol

        if (matLower == matUpper && strikeLower.compareTo(strikeUpper) == 0) {
            return volLookup(strikeLower, matLower)
        }

        if (matLower == matUpper) {
            return interpolate(
                clampedStrike, strikeLower, strikeUpper,
                volLookup(strikeLower, matLower),
                volLookup(strikeUpper, matLower),
            )
        }

        if (strikeLower.compareTo(strikeUpper) == 0) {
            return interpolate(
                clampedMaturity.toBigDecimal(), matLower.toBigDecimal(), matUpper.toBigDecimal(),
                volLookup(strikeLower, matLower),
                volLookup(strikeLower, matUpper),
            )
        }

        val volLL = volLookup(strikeLower, matLower)
        val volLU = volLookup(strikeLower, matUpper)
        val volUL = volLookup(strikeUpper, matLower)
        val volUU = volLookup(strikeUpper, matUpper)

        val volAtLowerStrike = interpolate(
            clampedMaturity.toBigDecimal(), matLower.toBigDecimal(), matUpper.toBigDecimal(),
            volLL, volLU,
        )
        val volAtUpperStrike = interpolate(
            clampedMaturity.toBigDecimal(), matLower.toBigDecimal(), matUpper.toBigDecimal(),
            volUL, volUU,
        )

        return interpolate(clampedStrike, strikeLower, strikeUpper, volAtLowerStrike, volAtUpperStrike)
    }

    fun scaleAll(factor: BigDecimal): VolSurface {
        require(factor > BigDecimal.ZERO) { "Scale factor must be positive, was $factor" }
        return copy(
            points = points.map { it.copy(impliedVol = it.impliedVol * factor) },
        )
    }

    companion object {
        fun flat(instrumentId: InstrumentId, asOf: Instant, impliedVol: BigDecimal): VolSurface {
            require(impliedVol > BigDecimal.ZERO) { "Implied vol must be positive" }
            return VolSurface(
                instrumentId = instrumentId,
                asOf = asOf,
                points = listOf(
                    VolPoint(BigDecimal("100"), 30, impliedVol),
                    VolPoint(BigDecimal("100"), 365, impliedVol),
                    VolPoint(BigDecimal("200"), 30, impliedVol),
                    VolPoint(BigDecimal("200"), 365, impliedVol),
                ),
            )
        }

        private fun interpolate(
            x: BigDecimal, x0: BigDecimal, x1: BigDecimal,
            y0: BigDecimal, y1: BigDecimal,
        ): BigDecimal {
            if (x0.compareTo(x1) == 0) return y0
            val range = x1 - x0
            val offset = x - x0
            return y0 + ((y1 - y0) * offset).divide(range, MathContext.DECIMAL128)
        }

        private fun clampStrike(strike: BigDecimal, sortedStrikes: List<BigDecimal>): BigDecimal =
            when {
                strike.compareTo(sortedStrikes.first()) < 0 -> sortedStrikes.first()
                strike.compareTo(sortedStrikes.last()) > 0 -> sortedStrikes.last()
                else -> strike
            }
    }
}
