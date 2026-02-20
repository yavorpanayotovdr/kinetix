package com.kinetix.marketdata.persistence

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.MarketDataPoint
import java.time.Instant

interface MarketDataRepository {
    suspend fun save(point: MarketDataPoint)
    suspend fun findLatest(instrumentId: InstrumentId): MarketDataPoint?
    suspend fun findByInstrumentId(instrumentId: InstrumentId, from: Instant, to: Instant): List<MarketDataPoint>
}
