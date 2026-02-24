package com.kinetix.risk.client.dtos

import com.kinetix.common.model.RateSource
import com.kinetix.common.model.Tenor
import com.kinetix.common.model.YieldCurve
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

@Serializable
data class TenorDto(
    val label: String,
    val days: Int,
    val rate: String,
) {
    fun toDomain(): Tenor = Tenor(
        label = label,
        days = days,
        rate = BigDecimal(rate),
    )
}

@Serializable
data class YieldCurveDto(
    val curveId: String,
    val currency: String,
    val tenors: List<TenorDto>,
    val asOfDate: String,
    val source: String,
) {
    fun toDomain(): YieldCurve = YieldCurve(
        currency = Currency.getInstance(currency),
        asOf = Instant.parse(asOfDate),
        tenors = tenors.map { it.toDomain() },
        curveId = curveId,
        source = RateSource.valueOf(source),
    )
}
