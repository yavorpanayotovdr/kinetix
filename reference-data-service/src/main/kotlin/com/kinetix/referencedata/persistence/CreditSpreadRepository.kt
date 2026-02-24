package com.kinetix.referencedata.persistence

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.InstrumentId
import java.time.Instant

interface CreditSpreadRepository {
    suspend fun save(creditSpread: CreditSpread)
    suspend fun findLatest(instrumentId: InstrumentId): CreditSpread?
    suspend fun findByTimeRange(instrumentId: InstrumentId, from: Instant, to: Instant): List<CreditSpread>
}
