package com.kinetix.gateway.client

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.MarketDataPoint
import java.time.Instant

interface MarketDataServiceClient {
    suspend fun getLatestPrice(instrumentId: InstrumentId): MarketDataPoint?
    suspend fun getPriceHistory(instrumentId: InstrumentId, from: Instant, to: Instant): List<MarketDataPoint>
}
