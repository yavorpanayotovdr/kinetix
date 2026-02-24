package com.kinetix.risk.client

import com.kinetix.common.model.Position
import com.kinetix.proto.risk.DataDependenciesRequest
import com.kinetix.proto.risk.DataDependenciesResponse
import com.kinetix.proto.risk.MarketDataDependenciesServiceGrpcKt.MarketDataDependenciesServiceCoroutineStub
import com.kinetix.proto.risk.RiskCalculationServiceGrpcKt.RiskCalculationServiceCoroutineStub
import com.kinetix.proto.risk.VaRRequest
import com.kinetix.risk.mapper.toProto
import com.kinetix.risk.mapper.toDomain
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.MarketDataValue
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.model.VaRResult
import com.kinetix.proto.common.PortfolioId as ProtoPortfolioId

class GrpcRiskEngineClient(
    private val stub: RiskCalculationServiceCoroutineStub,
    private val dependenciesStub: MarketDataDependenciesServiceCoroutineStub? = null,
) : RiskEngineClient {

    override suspend fun calculateVaR(
        request: VaRCalculationRequest,
        positions: List<Position>,
        marketData: List<MarketDataValue>,
    ): VaRResult {
        val protoRequest = VaRRequest.newBuilder()
            .setPortfolioId(ProtoPortfolioId.newBuilder().setValue(request.portfolioId.value))
            .setCalculationType(request.calculationType.toProto())
            .setConfidenceLevel(request.confidenceLevel.toProto())
            .setTimeHorizonDays(request.timeHorizonDays)
            .setNumSimulations(request.numSimulations)
            .addAllPositions(positions.map { it.toProto() })
            .addAllMarketData(marketData.map { it.toProto() })
            .build()

        val response = stub.calculateVaR(protoRequest)
        return response.toDomain()
    }

    override suspend fun discoverDependencies(
        positions: List<Position>,
        calculationType: String,
        confidenceLevel: String,
    ): DataDependenciesResponse {
        val calcType = CalculationType.valueOf(calculationType)
        val confLevel = ConfidenceLevel.valueOf(confidenceLevel)

        val protoRequest = DataDependenciesRequest.newBuilder()
            .addAllPositions(positions.map { it.toProto() })
            .setCalculationType(calcType.toProto())
            .setConfidenceLevel(confLevel.toProto())
            .build()

        return requireNotNull(dependenciesStub) {
            "MarketDataDependenciesService stub not configured"
        }.discoverDependencies(protoRequest)
    }
}
