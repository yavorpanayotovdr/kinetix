package com.kinetix.rates.persistence

import com.kinetix.common.model.RiskFreeRate
import java.time.Instant
import java.util.Currency

interface RiskFreeRateRepository {
    suspend fun save(rate: RiskFreeRate)
    suspend fun findLatest(currency: Currency, tenor: String): RiskFreeRate?
    suspend fun findByTimeRange(currency: Currency, tenor: String, from: Instant, to: Instant): List<RiskFreeRate>
}
