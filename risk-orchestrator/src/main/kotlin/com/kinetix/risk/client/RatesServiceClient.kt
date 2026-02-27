package com.kinetix.risk.client

import com.kinetix.common.model.ForwardCurve
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.RiskFreeRate
import com.kinetix.common.model.YieldCurve
import java.util.Currency

interface RatesServiceClient {
    suspend fun getLatestYieldCurve(curveId: String): ClientResponse<YieldCurve>
    suspend fun getLatestRiskFreeRate(currency: Currency, tenor: String): ClientResponse<RiskFreeRate>
    suspend fun getLatestForwardCurve(instrumentId: InstrumentId): ClientResponse<ForwardCurve>
}
