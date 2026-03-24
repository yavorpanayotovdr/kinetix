package com.kinetix.risk.client

import com.kinetix.common.model.LiquidityRiskResult
import com.kinetix.common.model.LiquidityTier
import com.kinetix.common.model.PositionLiquidityRisk
import com.kinetix.proto.common.AssetClass as ProtoAssetClass
import com.kinetix.proto.common.BookId as ProtoBookId
import com.kinetix.proto.risk.LiquidityAdjustedVaRRequest
import com.kinetix.proto.risk.LiquidityInput as ProtoLiquidityInput
import com.kinetix.proto.risk.LiquidityRiskServiceGrpcKt.LiquidityRiskServiceCoroutineStub
import com.kinetix.proto.risk.LiquidityTier as ProtoLiquidityTier
import com.kinetix.proto.risk.PositionLiquidityRisk as ProtoPositionLiquidityRisk
import com.kinetix.risk.client.dtos.InstrumentLiquidityDto
import java.util.concurrent.TimeUnit

data class LiquidityInputRequest(
    val instrumentId: String,
    val marketValue: Double,
    val liquidityDto: InstrumentLiquidityDto?,
    val assetClass: String,
)

class GrpcLiquidityClient(
    private val stub: LiquidityRiskServiceCoroutineStub,
    private val deadlineMs: Long = 60_000,
) {

    suspend fun calculateLiquidityAdjustedVaR(
        bookId: String,
        baseVar: Double,
        baseHoldingPeriod: Int = 1,
        liquidityInputs: List<LiquidityInputRequest>,
        stressFactors: Map<String, Double> = emptyMap(),
        portfolioDailyVol: Double = 0.015,
    ): LiquidityRiskResult {
        val protoInputs = liquidityInputs.map { inp ->
            val dto = inp.liquidityDto
            val protoAc = ASSET_CLASS_TO_PROTO[inp.assetClass] ?: ProtoAssetClass.EQUITY
            LiquidityInput.newBuilder()
                .setInstrumentId(inp.instrumentId)
                .setMarketValue(inp.marketValue)
                .setAdv(dto?.adv ?: 0.0)
                .setAdvMissing(dto == null)
                .setAdvStalenessDays(dto?.advStalenessDays ?: 0)
                .setAssetClass(protoAc)
                .build()
        }

        val request = LiquidityAdjustedVaRRequest.newBuilder()
            .setBookId(ProtoBookId.newBuilder().setValue(bookId))
            .setBaseVar(baseVar)
            .setBaseHoldingPeriod(baseHoldingPeriod)
            .addAllInputs(protoInputs)
            .putAllStressFactors(stressFactors)
            .setPortfolioDailyVol(portfolioDailyVol)
            .build()

        val response = stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
            .calculateLiquidityAdjustedVaR(request)

        return LiquidityRiskResult(
            bookId = response.bookId.value,
            portfolioLvar = response.portfolioLvar,
            dataCompleteness = response.dataCompleteness,
            positionRisks = response.positionRisksList.map { it.toDomain() },
            portfolioConcentrationStatus = response.portfolioConcentrationStatus,
            calculatedAt = response.calculatedAt.toString(),
        )
    }
}

private fun ProtoPositionLiquidityRisk.toDomain() = PositionLiquidityRisk(
    instrumentId = instrumentId,
    assetClass = assetClass.name,
    marketValue = marketValue,
    tier = PROTO_TIER_TO_DOMAIN[tier] ?: LiquidityTier.ILLIQUID,
    horizonDays = horizonDays,
    adv = if (advMissing) null else adv,
    advMissing = advMissing,
    advStale = advStale,
    lvarContribution = lvarContribution,
    stressedLiquidationValue = stressedLiquidationValue,
    concentrationStatus = concentrationStatus,
)

private val PROTO_TIER_TO_DOMAIN = mapOf(
    ProtoLiquidityTier.HIGH_LIQUID to LiquidityTier.HIGH_LIQUID,
    ProtoLiquidityTier.LIQUID to LiquidityTier.LIQUID,
    ProtoLiquidityTier.SEMI_LIQUID to LiquidityTier.SEMI_LIQUID,
    ProtoLiquidityTier.ILLIQUID to LiquidityTier.ILLIQUID,
)

private val ASSET_CLASS_TO_PROTO = mapOf(
    "EQUITY" to ProtoAssetClass.EQUITY,
    "FIXED_INCOME" to ProtoAssetClass.FIXED_INCOME,
    "FX" to ProtoAssetClass.FX,
    "COMMODITY" to ProtoAssetClass.COMMODITY,
    "DERIVATIVE" to ProtoAssetClass.DERIVATIVE,
)

// Type alias so proto-generated LiquidityInput is importable with a clear name
private typealias LiquidityInput = com.kinetix.proto.risk.LiquidityInput
