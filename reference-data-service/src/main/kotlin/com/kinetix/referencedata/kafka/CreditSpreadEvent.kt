package com.kinetix.referencedata.kafka

import com.kinetix.common.model.CreditSpread
import kotlinx.serialization.Serializable

@Serializable
data class CreditSpreadEvent(
    val instrumentId: String,
    val spread: Double,
    val rating: String?,
    val asOfDate: String,
    val source: String,
) {
    companion object {
        fun from(cs: CreditSpread) = CreditSpreadEvent(
            instrumentId = cs.instrumentId.value,
            spread = cs.spread,
            rating = cs.rating,
            asOfDate = cs.asOfDate.toString(),
            source = cs.source.name,
        )
    }
}
