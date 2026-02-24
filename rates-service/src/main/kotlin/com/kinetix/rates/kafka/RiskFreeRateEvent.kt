package com.kinetix.rates.kafka

import com.kinetix.common.model.RiskFreeRate
import kotlinx.serialization.Serializable

@Serializable
data class RiskFreeRateEvent(
    val currency: String,
    val tenor: String,
    val rate: String,
    val asOfDate: String,
    val source: String,
) {
    companion object {
        fun from(rate: RiskFreeRate) = RiskFreeRateEvent(
            currency = rate.currency.currencyCode,
            tenor = rate.tenor,
            rate = rate.rate.toBigDecimal().toPlainString(),
            asOfDate = rate.asOfDate.toString(),
            source = rate.source.name,
        )
    }
}
