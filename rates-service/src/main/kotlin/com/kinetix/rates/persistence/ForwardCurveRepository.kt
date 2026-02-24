package com.kinetix.rates.persistence

import com.kinetix.common.model.ForwardCurve
import com.kinetix.common.model.InstrumentId
import java.time.Instant

interface ForwardCurveRepository {
    suspend fun save(curve: ForwardCurve)
    suspend fun findLatest(instrumentId: InstrumentId): ForwardCurve?
    suspend fun findByTimeRange(instrumentId: InstrumentId, from: Instant, to: Instant): List<ForwardCurve>
}
