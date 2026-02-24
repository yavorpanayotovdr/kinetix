package com.kinetix.referencedata.persistence

import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import java.time.Instant

interface DividendYieldRepository {
    suspend fun save(dividendYield: DividendYield)
    suspend fun findLatest(instrumentId: InstrumentId): DividendYield?
    suspend fun findByTimeRange(instrumentId: InstrumentId, from: Instant, to: Instant): List<DividendYield>
}
