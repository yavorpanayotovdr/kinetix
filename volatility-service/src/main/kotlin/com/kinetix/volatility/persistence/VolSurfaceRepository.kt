package com.kinetix.volatility.persistence

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.VolSurface
import java.time.Instant

interface VolSurfaceRepository {
    suspend fun save(surface: VolSurface)
    suspend fun findLatest(instrumentId: InstrumentId): VolSurface?
    suspend fun findByTimeRange(instrumentId: InstrumentId, from: Instant, to: Instant): List<VolSurface>
}
