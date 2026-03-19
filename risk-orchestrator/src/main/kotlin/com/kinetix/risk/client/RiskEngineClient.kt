package com.kinetix.risk.client

import com.kinetix.common.model.Position
import com.kinetix.proto.risk.DataDependenciesResponse
import com.kinetix.risk.client.dtos.InstrumentDto
import com.kinetix.risk.model.MarketDataValue
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.model.VaRResult
import com.kinetix.risk.model.ValuationResult

interface RiskEngineClient {
    suspend fun calculateVaR(
        request: VaRCalculationRequest,
        positions: List<Position>,
        marketData: List<MarketDataValue> = emptyList(),
        instrumentMap: Map<String, InstrumentDto> = emptyMap(),
    ): VaRResult
    suspend fun valuate(
        request: VaRCalculationRequest,
        positions: List<Position>,
        marketData: List<MarketDataValue> = emptyList(),
        instrumentMap: Map<String, InstrumentDto> = emptyMap(),
    ): ValuationResult
    suspend fun discoverDependencies(
        positions: List<Position>,
        calculationType: String,
        confidenceLevel: String,
        instrumentMap: Map<String, InstrumentDto> = emptyMap(),
    ): DataDependenciesResponse
}
