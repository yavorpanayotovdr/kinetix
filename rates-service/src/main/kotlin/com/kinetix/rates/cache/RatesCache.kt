package com.kinetix.rates.cache

import com.kinetix.common.model.ForwardCurve
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.RiskFreeRate
import com.kinetix.common.model.YieldCurve
import java.util.Currency

interface RatesCache {
    suspend fun putYieldCurve(curve: YieldCurve)
    suspend fun getYieldCurve(curveId: String): YieldCurve?

    suspend fun putRiskFreeRate(rate: RiskFreeRate)
    suspend fun getRiskFreeRate(currency: Currency, tenor: String): RiskFreeRate?

    suspend fun putForwardCurve(curve: ForwardCurve)
    suspend fun getForwardCurve(instrumentId: InstrumentId): ForwardCurve?
}
