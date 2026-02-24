package com.kinetix.common.model

import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import java.util.Currency

data class YieldCurve(
    val currency: Currency,
    val asOf: Instant,
    val tenors: List<Tenor>,
    val curveId: String = currency.currencyCode,
    val source: RateSource = RateSource.INTERNAL,
) {
    init {
        require(curveId.isNotBlank()) { "curveId must not be blank" }
        require(tenors.isNotEmpty()) { "YieldCurve must have at least one tenor" }
        require(tenors == tenors.sorted()) {
            "YieldCurve tenors must be sorted by maturity (days)"
        }
        require(tenors.map { it.days }.distinct().size == tenors.size) {
            "YieldCurve must not contain duplicate tenor maturities"
        }
    }

    val shortRate: BigDecimal
        get() = tenors.first().rate

    val longRate: BigDecimal
        get() = tenors.last().rate

    fun rateAt(days: Int): BigDecimal {
        require(days > 0) { "Maturity days must be positive, was $days" }

        tenors.find { it.days == days }?.let { return it.rate }

        if (days <= tenors.first().days) return tenors.first().rate
        if (days >= tenors.last().days) return tenors.last().rate

        val upper = tenors.first { it.days >= days }
        val lower = tenors.last { it.days <= days }

        val dayRange = (upper.days - lower.days).toBigDecimal()
        val rateRange = upper.rate - lower.rate
        val dayOffset = (days - lower.days).toBigDecimal()

        return lower.rate + (rateRange * dayOffset).divide(dayRange, MathContext.DECIMAL128)
    }

    fun shift(basisPoints: BigDecimal): YieldCurve {
        val shiftDecimal = basisPoints.divide(BigDecimal("10000"), MathContext.DECIMAL128)
        return copy(
            tenors = tenors.map { it.copy(rate = it.rate + shiftDecimal) },
        )
    }

    companion object {
        fun flat(currency: Currency, asOf: Instant, rate: BigDecimal): YieldCurve =
            YieldCurve(
                currency = currency,
                asOf = asOf,
                tenors = listOf(
                    Tenor.oneMonth(rate),
                    Tenor.oneYear(rate),
                    Tenor.tenYears(rate),
                ),
            )
    }
}
