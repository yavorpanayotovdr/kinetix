package com.kinetix.rates.kafka

import com.kinetix.common.model.YieldCurve
import kotlinx.serialization.Serializable

@Serializable
data class TenorEvent(val label: String, val days: Int, val rate: String)

@Serializable
data class YieldCurveEvent(
    val curveId: String,
    val currency: String,
    val tenors: List<TenorEvent>,
    val asOfDate: String,
    val source: String,
) {
    companion object {
        fun from(curve: YieldCurve) = YieldCurveEvent(
            curveId = curve.curveId,
            currency = curve.currency.currencyCode,
            tenors = curve.tenors.map { TenorEvent(it.label, it.days, it.rate.toPlainString()) },
            asOfDate = curve.asOf.toString(),
            source = curve.source.name,
        )
    }
}
