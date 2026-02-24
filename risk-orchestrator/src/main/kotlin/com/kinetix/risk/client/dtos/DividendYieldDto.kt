package com.kinetix.risk.client.dtos

import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.ReferenceDataSource
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

@Serializable
data class DividendYieldDto(
    val instrumentId: String,
    val yield: Double,
    val exDate: String?,
    val asOfDate: String,
    val source: String,
) {
    fun toDomain() = DividendYield(
        instrumentId = InstrumentId(instrumentId),
        yield = yield,
        exDate = exDate?.let { LocalDate.parse(it) },
        asOfDate = Instant.parse(asOfDate),
        source = ReferenceDataSource.valueOf(source),
    )
}
