package com.kinetix.risk.simulation

import com.kinetix.common.model.Position
import com.kinetix.proto.risk.DataDependenciesResponse
import com.kinetix.risk.client.RiskEngineClient
import com.kinetix.risk.model.MarketDataValue
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.model.VaRResult
import kotlinx.coroutines.delay

class DelayingRiskEngineClient(
    private val delegate: RiskEngineClient,
    private val discoverDependenciesDelayMs: LongRange,
    private val calculateVaRDelayMs: LongRange,
) : RiskEngineClient {

    override suspend fun calculateVaR(
        request: VaRCalculationRequest,
        positions: List<Position>,
        marketData: List<MarketDataValue>,
    ): VaRResult {
        delay(calculateVaRDelayMs.random())
        return delegate.calculateVaR(request, positions, marketData)
    }

    override suspend fun discoverDependencies(
        positions: List<Position>,
        calculationType: String,
        confidenceLevel: String,
    ): DataDependenciesResponse {
        delay(discoverDependenciesDelayMs.random())
        return delegate.discoverDependencies(positions, calculationType, confidenceLevel)
    }
}
