package com.kinetix.risk.simulation

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PricePoint
import com.kinetix.risk.client.ClientResponse
import com.kinetix.risk.client.PriceServiceClient
import kotlinx.coroutines.delay
import java.time.Instant

class DelayingPriceServiceClient(
    private val delegate: PriceServiceClient,
    private val delayMs: LongRange,
) : PriceServiceClient {

    override suspend fun getLatestPrice(instrumentId: InstrumentId): ClientResponse<PricePoint> {
        delay(delayMs.random())
        return delegate.getLatestPrice(instrumentId)
    }

    override suspend fun getPriceHistory(instrumentId: InstrumentId, from: Instant, to: Instant): ClientResponse<List<PricePoint>> {
        delay(delayMs.random())
        return delegate.getPriceHistory(instrumentId, from, to)
    }
}
