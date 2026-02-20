package com.kinetix.risk.client

import com.kinetix.common.model.Position
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.model.VaRResult

interface RiskEngineClient {
    suspend fun calculateVaR(request: VaRCalculationRequest, positions: List<Position>): VaRResult
}
