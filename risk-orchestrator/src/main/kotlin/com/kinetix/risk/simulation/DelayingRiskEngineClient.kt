package com.kinetix.risk.simulation

import com.kinetix.common.model.Position
import com.kinetix.proto.risk.DataDependenciesResponse
import com.kinetix.risk.client.RiskEngineClient
import com.kinetix.risk.client.dtos.InstrumentDto
import com.kinetix.risk.model.MarketDataValue
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.model.VaRResult
import com.kinetix.risk.model.ValuationResult
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
        instrumentMap: Map<String, InstrumentDto>,
    ): VaRResult {
        delay(calculateVaRDelayMs.random())
        return delegate.calculateVaR(request, positions, marketData, instrumentMap)
    }

    override suspend fun valuate(
        request: VaRCalculationRequest,
        positions: List<Position>,
        marketData: List<MarketDataValue>,
        instrumentMap: Map<String, InstrumentDto>,
    ): ValuationResult {
        delay(calculateVaRDelayMs.random())
        return delegate.valuate(request, positions, marketData, instrumentMap)
    }

    override suspend fun discoverDependencies(
        positions: List<Position>,
        calculationType: String,
        confidenceLevel: String,
        instrumentMap: Map<String, InstrumentDto>,
    ): DataDependenciesResponse {
        delay(discoverDependenciesDelayMs.random())
        return delegate.discoverDependencies(positions, calculationType, confidenceLevel, instrumentMap)
    }
}
