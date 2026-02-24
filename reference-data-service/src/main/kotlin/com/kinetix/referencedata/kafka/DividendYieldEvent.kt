package com.kinetix.referencedata.kafka

import com.kinetix.common.model.DividendYield
import kotlinx.serialization.Serializable

@Serializable
data class DividendYieldEvent(
    val instrumentId: String,
    val yield: Double,
    val exDate: String?,
    val asOfDate: String,
    val source: String,
) {
    companion object {
        fun from(d: DividendYield) = DividendYieldEvent(
            instrumentId = d.instrumentId.value,
            yield = d.yield,
            exDate = d.exDate?.toString(),
            asOfDate = d.asOfDate.toString(),
            source = d.source.name,
        )
    }
}
