package com.kinetix.risk.client.dtos

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.VolPoint
import com.kinetix.common.model.VolSurface
import com.kinetix.common.model.VolatilitySource
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class VolPointDto(
    val strike: Double,
    val maturityDays: Int,
    val impliedVol: Double,
)

@Serializable
data class VolSurfaceDto(
    val instrumentId: String,
    val asOfDate: String,
    val points: List<VolPointDto>,
    val source: String,
) {
    fun toDomain() = VolSurface(
        instrumentId = InstrumentId(instrumentId),
        asOf = Instant.parse(asOfDate),
        points = points.map { VolPoint(it.strike.toBigDecimal(), it.maturityDays, it.impliedVol.toBigDecimal()) },
        source = VolatilitySource.valueOf(source),
    )
}
