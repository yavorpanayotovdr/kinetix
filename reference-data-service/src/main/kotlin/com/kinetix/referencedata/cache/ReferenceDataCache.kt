package com.kinetix.referencedata.cache

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId

interface ReferenceDataCache {
    suspend fun putDividendYield(dividendYield: DividendYield)
    suspend fun getDividendYield(instrumentId: InstrumentId): DividendYield?

    suspend fun putCreditSpread(creditSpread: CreditSpread)
    suspend fun getCreditSpread(instrumentId: InstrumentId): CreditSpread?
}
