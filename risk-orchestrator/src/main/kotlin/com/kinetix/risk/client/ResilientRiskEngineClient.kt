package com.kinetix.risk.client

import com.kinetix.common.model.Position
import com.kinetix.common.resilience.CircuitBreaker
import com.kinetix.proto.risk.DataDependenciesResponse
import com.kinetix.risk.model.MarketDataValue
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.model.VaRResult
import com.kinetix.risk.model.ValuationResult

class ResilientRiskEngineClient(
    private val delegate: RiskEngineClient,
    private val circuitBreaker: CircuitBreaker,
) : RiskEngineClient {

    override suspend fun calculateVaR(
        request: VaRCalculationRequest,
        positions: List<Position>,
        marketData: List<MarketDataValue>,
    ): VaRResult =
        circuitBreaker.execute { delegate.calculateVaR(request, positions, marketData) }

    override suspend fun valuate(
        request: VaRCalculationRequest,
        positions: List<Position>,
        marketData: List<MarketDataValue>,
    ): ValuationResult =
        circuitBreaker.execute { delegate.valuate(request, positions, marketData) }

    override suspend fun discoverDependencies(
        positions: List<Position>,
        calculationType: String,
        confidenceLevel: String,
    ): DataDependenciesResponse =
        circuitBreaker.execute { delegate.discoverDependencies(positions, calculationType, confidenceLevel) }
}
