package com.kinetix.risk.simulation

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.VolSurface
import com.kinetix.risk.client.VolatilityServiceClient
import kotlinx.coroutines.delay

class DelayingVolatilityServiceClient(
    private val delegate: VolatilityServiceClient,
    private val delayMs: LongRange,
) : VolatilityServiceClient {

    override suspend fun getLatestSurface(instrumentId: InstrumentId): VolSurface? {
        delay(delayMs.random())
        return delegate.getLatestSurface(instrumentId)
    }
}
