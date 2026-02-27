package com.kinetix.risk.client

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PricePoint
import java.time.Instant

interface PriceServiceClient {
    suspend fun getLatestPrice(instrumentId: InstrumentId): ClientResponse<PricePoint>
    suspend fun getPriceHistory(instrumentId: InstrumentId, from: Instant, to: Instant): ClientResponse<List<PricePoint>>
}
