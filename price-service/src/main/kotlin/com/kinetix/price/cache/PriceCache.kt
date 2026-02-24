package com.kinetix.price.cache

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PricePoint

interface PriceCache {
    suspend fun put(point: PricePoint)
    suspend fun get(instrumentId: InstrumentId): PricePoint?
}
