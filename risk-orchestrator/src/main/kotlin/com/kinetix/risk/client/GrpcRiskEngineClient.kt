package com.kinetix.risk.client

import com.kinetix.common.model.Position
import com.kinetix.proto.risk.RiskCalculationServiceGrpcKt.RiskCalculationServiceCoroutineStub
import com.kinetix.proto.risk.VaRRequest
import com.kinetix.risk.mapper.toProto
import com.kinetix.risk.mapper.toDomain
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.model.VaRResult
import com.kinetix.proto.common.PortfolioId as ProtoPortfolioId

class GrpcRiskEngineClient(
    private val stub: RiskCalculationServiceCoroutineStub,
) : RiskEngineClient {

    override suspend fun calculateVaR(request: VaRCalculationRequest, positions: List<Position>): VaRResult {
        val protoRequest = VaRRequest.newBuilder()
            .setPortfolioId(ProtoPortfolioId.newBuilder().setValue(request.portfolioId.value))
            .setCalculationType(request.calculationType.toProto())
            .setConfidenceLevel(request.confidenceLevel.toProto())
            .setTimeHorizonDays(request.timeHorizonDays)
            .setNumSimulations(request.numSimulations)
            .addAllPositions(positions.map { it.toProto() })
            .build()

        val response = stub.calculateVaR(protoRequest)
        return response.toDomain()
    }
}
