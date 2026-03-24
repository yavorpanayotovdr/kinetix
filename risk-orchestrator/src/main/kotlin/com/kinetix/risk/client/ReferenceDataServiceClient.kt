package com.kinetix.risk.client

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import com.kinetix.risk.client.dtos.InstrumentLiquidityDto

interface ReferenceDataServiceClient {
    suspend fun getLatestDividendYield(instrumentId: InstrumentId): ClientResponse<DividendYield>
    suspend fun getLatestCreditSpread(instrumentId: InstrumentId): ClientResponse<CreditSpread>
    suspend fun getLiquidityData(instrumentId: String): ClientResponse<InstrumentLiquidityDto>
    suspend fun getLiquidityDataBatch(instrumentIds: List<String>): Map<String, InstrumentLiquidityDto>
}
