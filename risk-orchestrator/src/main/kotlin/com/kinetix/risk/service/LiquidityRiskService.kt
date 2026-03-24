package com.kinetix.risk.service

import com.kinetix.common.model.BookId
import com.kinetix.common.model.LiquidityRiskResult
import com.kinetix.risk.client.GrpcLiquidityClient
import com.kinetix.risk.client.LiquidityInputRequest
import com.kinetix.risk.client.PositionProvider
import com.kinetix.risk.client.ReferenceDataServiceClient
import com.kinetix.risk.persistence.LiquidityRiskSnapshotRepository
import org.slf4j.LoggerFactory

class LiquidityRiskService(
    private val positionProvider: PositionProvider,
    private val referenceDataClient: ReferenceDataServiceClient,
    private val grpcLiquidityClient: GrpcLiquidityClient,
    private val repository: LiquidityRiskSnapshotRepository,
    private val baseHoldingPeriod: Int = 1,
    private val portfolioDailyVol: Double = 0.015,
    private val stressFactors: Map<String, Double> = emptyMap(),
) {
    private val logger = LoggerFactory.getLogger(LiquidityRiskService::class.java)

    suspend fun calculateAndSave(bookId: BookId, baseVar: Double): LiquidityRiskResult? {
        val positions = positionProvider.getPositions(bookId)
        if (positions.isEmpty()) {
            logger.info("No positions found for book {}, skipping liquidity risk calculation", bookId.value)
            return null
        }

        val instrumentIds = positions.map { it.instrumentId.value }
        val liquidityByInstrument = try {
            referenceDataClient.getLiquidityDataBatch(instrumentIds)
        } catch (e: Exception) {
            logger.warn("Failed to fetch ADV data for book {}, proceeding with all positions marked advMissing", bookId.value, e)
            emptyMap()
        }

        val liquidityInputs = positions.map { position ->
            LiquidityInputRequest(
                instrumentId = position.instrumentId.value,
                marketValue = position.marketValue.amount.toDouble(),
                liquidityDto = liquidityByInstrument[position.instrumentId.value],
                assetClass = position.assetClass.name,
            )
        }

        logger.info(
            "Calculating liquidity-adjusted VaR for book {} with {} positions ({} with ADV data)",
            bookId.value, positions.size, liquidityByInstrument.size,
        )

        val result = grpcLiquidityClient.calculateLiquidityAdjustedVaR(
            bookId = bookId.value,
            baseVar = baseVar,
            baseHoldingPeriod = baseHoldingPeriod,
            liquidityInputs = liquidityInputs,
            stressFactors = stressFactors,
            portfolioDailyVol = portfolioDailyVol,
        )

        repository.save(result)

        logger.info(
            "Liquidity risk calculated for book {}: LVaR={}, dataCompleteness={}, concentrationStatus={}",
            bookId.value, result.portfolioLvar, result.dataCompleteness, result.portfolioConcentrationStatus,
        )

        return result
    }
}
