package com.kinetix.risk.client

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId

interface ReferenceDataServiceClient {
    suspend fun getLatestDividendYield(instrumentId: InstrumentId): DividendYield?
    suspend fun getLatestCreditSpread(instrumentId: InstrumentId): CreditSpread?
}
