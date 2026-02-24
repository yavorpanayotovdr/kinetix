package com.kinetix.risk.client.dtos

import com.kinetix.common.model.RateSource
import com.kinetix.common.model.RiskFreeRate
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.Currency

@Serializable
data class RiskFreeRateDto(
    val currency: String,
    val tenor: String,
    val rate: String,
    val asOfDate: String,
    val source: String,
) {
    fun toDomain(): RiskFreeRate = RiskFreeRate(
        currency = Currency.getInstance(currency),
        tenor = tenor,
        rate = rate.toDouble(),
        asOfDate = Instant.parse(asOfDate),
        source = RateSource.valueOf(source),
    )
}
