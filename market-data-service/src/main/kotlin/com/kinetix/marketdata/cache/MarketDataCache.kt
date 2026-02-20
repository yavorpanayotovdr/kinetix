package com.kinetix.marketdata.cache

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.MarketDataPoint

interface MarketDataCache {
    suspend fun put(point: MarketDataPoint)
    suspend fun get(instrumentId: InstrumentId): MarketDataPoint?
}
