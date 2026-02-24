package com.kinetix.referencedata.kafka

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.DividendYield

interface ReferenceDataPublisher {
    suspend fun publishDividendYield(dividendYield: DividendYield)
    suspend fun publishCreditSpread(creditSpread: CreditSpread)
}
