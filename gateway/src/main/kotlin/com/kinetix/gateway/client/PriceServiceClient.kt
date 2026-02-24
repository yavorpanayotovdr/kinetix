package com.kinetix.gateway.client

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PricePoint
import java.time.Instant

interface PriceServiceClient {
    suspend fun getLatestPrice(instrumentId: InstrumentId): PricePoint?
    suspend fun getPriceHistory(instrumentId: InstrumentId, from: Instant, to: Instant): List<PricePoint>
}
