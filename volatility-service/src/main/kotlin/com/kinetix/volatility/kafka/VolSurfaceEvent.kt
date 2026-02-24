package com.kinetix.volatility.kafka

import com.kinetix.common.model.VolSurface
import kotlinx.serialization.Serializable

@Serializable
data class VolSurfaceEvent(
    val instrumentId: String,
    val asOfDate: String,
    val pointCount: Int,
    val source: String,
) {
    companion object {
        fun from(s: VolSurface) = VolSurfaceEvent(
            instrumentId = s.instrumentId.value,
            asOfDate = s.asOf.toString(),
            pointCount = s.points.size,
            source = s.source.name,
        )
    }
}
