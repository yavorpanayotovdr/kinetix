package com.kinetix.rates.service

import com.kinetix.common.model.ForwardCurve
import com.kinetix.common.model.RiskFreeRate
import com.kinetix.common.model.YieldCurve
import com.kinetix.rates.cache.RatesCache
import com.kinetix.rates.kafka.RatesPublisher
import com.kinetix.rates.persistence.ForwardCurveRepository
import com.kinetix.rates.persistence.RiskFreeRateRepository
import com.kinetix.rates.persistence.YieldCurveRepository

class RateIngestionService(
    private val yieldCurveRepository: YieldCurveRepository,
    private val riskFreeRateRepository: RiskFreeRateRepository,
    private val forwardCurveRepository: ForwardCurveRepository,
    private val cache: RatesCache,
    private val publisher: RatesPublisher,
) {
    suspend fun ingest(curve: YieldCurve) {
        yieldCurveRepository.save(curve)
        cache.putYieldCurve(curve)
        publisher.publishYieldCurve(curve)
    }

    suspend fun ingest(rate: RiskFreeRate) {
        riskFreeRateRepository.save(rate)
        cache.putRiskFreeRate(rate)
        publisher.publishRiskFreeRate(rate)
    }

    suspend fun ingest(curve: ForwardCurve) {
        forwardCurveRepository.save(curve)
        cache.putForwardCurve(curve)
        publisher.publishForwardCurve(curve)
    }
}
