package com.kinetix.rates.persistence

import com.kinetix.common.model.YieldCurve

interface YieldCurveRepository {
    suspend fun save(curve: YieldCurve)
    suspend fun findLatest(curveId: String): YieldCurve?
    suspend fun findByTimeRange(curveId: String, from: java.time.Instant, to: java.time.Instant): List<YieldCurve>
}
