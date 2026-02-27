package com.kinetix.risk.simulation

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import com.kinetix.risk.client.ClientResponse
import com.kinetix.risk.client.ReferenceDataServiceClient
import kotlinx.coroutines.delay

class DelayingReferenceDataServiceClient(
    private val delegate: ReferenceDataServiceClient,
    private val delayMs: LongRange,
) : ReferenceDataServiceClient {

    override suspend fun getLatestDividendYield(instrumentId: InstrumentId): ClientResponse<DividendYield> {
        delay(delayMs.random())
        return delegate.getLatestDividendYield(instrumentId)
    }

    override suspend fun getLatestCreditSpread(instrumentId: InstrumentId): ClientResponse<CreditSpread> {
        delay(delayMs.random())
        return delegate.getLatestCreditSpread(instrumentId)
    }
}
