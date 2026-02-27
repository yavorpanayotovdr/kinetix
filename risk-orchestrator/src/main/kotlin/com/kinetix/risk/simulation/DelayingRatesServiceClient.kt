package com.kinetix.risk.simulation

import com.kinetix.common.model.ForwardCurve
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.RiskFreeRate
import com.kinetix.common.model.YieldCurve
import com.kinetix.risk.client.ClientResponse
import com.kinetix.risk.client.RatesServiceClient
import kotlinx.coroutines.delay
import java.util.Currency

class DelayingRatesServiceClient(
    private val delegate: RatesServiceClient,
    private val delayMs: LongRange,
) : RatesServiceClient {

    override suspend fun getLatestYieldCurve(curveId: String): ClientResponse<YieldCurve> {
        delay(delayMs.random())
        return delegate.getLatestYieldCurve(curveId)
    }

    override suspend fun getLatestRiskFreeRate(currency: Currency, tenor: String): ClientResponse<RiskFreeRate> {
        delay(delayMs.random())
        return delegate.getLatestRiskFreeRate(currency, tenor)
    }

    override suspend fun getLatestForwardCurve(instrumentId: InstrumentId): ClientResponse<ForwardCurve> {
        delay(delayMs.random())
        return delegate.getLatestForwardCurve(instrumentId)
    }
}
