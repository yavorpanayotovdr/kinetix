package com.kinetix.rates.kafka

import com.kinetix.common.model.ForwardCurve
import com.kinetix.common.model.RiskFreeRate
import com.kinetix.common.model.YieldCurve

interface RatesPublisher {
    suspend fun publishYieldCurve(curve: YieldCurve)
    suspend fun publishRiskFreeRate(rate: RiskFreeRate)
    suspend fun publishForwardCurve(curve: ForwardCurve)
}
