package com.kinetix.risk.simulation

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import com.kinetix.risk.client.ClientResponse
import com.kinetix.risk.client.ReferenceDataServiceClient
import com.kinetix.risk.client.dtos.InstrumentLiquidityDto
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

    override suspend fun getLiquidityData(instrumentId: String): ClientResponse<InstrumentLiquidityDto> {
        delay(delayMs.random())
        return delegate.getLiquidityData(instrumentId)
    }

    override suspend fun getLiquidityDataBatch(instrumentIds: List<String>): Map<String, InstrumentLiquidityDto> {
        delay(delayMs.random())
        return delegate.getLiquidityDataBatch(instrumentIds)
    }
}
