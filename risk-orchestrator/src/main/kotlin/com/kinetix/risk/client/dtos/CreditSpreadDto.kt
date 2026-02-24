package com.kinetix.risk.client.dtos

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.ReferenceDataSource
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class CreditSpreadDto(
    val instrumentId: String,
    val spread: Double,
    val rating: String?,
    val asOfDate: String,
    val source: String,
) {
    fun toDomain() = CreditSpread(
        instrumentId = InstrumentId(instrumentId),
        spread = spread,
        rating = rating,
        asOfDate = Instant.parse(asOfDate),
        source = ReferenceDataSource.valueOf(source),
    )
}
