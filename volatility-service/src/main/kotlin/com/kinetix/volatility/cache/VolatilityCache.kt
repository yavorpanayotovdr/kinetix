package com.kinetix.volatility.cache

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.VolSurface

interface VolatilityCache {
    suspend fun putSurface(surface: VolSurface)
    suspend fun getSurface(instrumentId: InstrumentId): VolSurface?
}
