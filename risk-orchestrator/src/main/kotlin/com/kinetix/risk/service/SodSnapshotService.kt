package com.kinetix.risk.service

import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.cache.LatestVaRCache
import com.kinetix.risk.client.PositionProvider
import com.kinetix.risk.model.*
import com.kinetix.risk.persistence.DailyRiskSnapshotRepository
import com.kinetix.risk.persistence.SodBaselineRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate

class SodSnapshotService(
    private val sodBaselineRepository: SodBaselineRepository,
    private val dailyRiskSnapshotRepository: DailyRiskSnapshotRepository,
    private val varCache: LatestVaRCache,
    private val varCalculationService: VaRCalculationService,
    private val positionProvider: PositionProvider,
) {
    private val logger = LoggerFactory.getLogger(SodSnapshotService::class.java)

    suspend fun createSnapshot(
        portfolioId: PortfolioId,
        snapshotType: SnapshotType,
        valuationResult: ValuationResult? = null,
        date: LocalDate = LocalDate.now(),
    ) {
        val result = valuationResult
            ?: varCache.get(portfolioId.value)
            ?: calculateFreshVaR(portfolioId)
            ?: throw IllegalStateException("Cannot create SOD snapshot: no valuation data available for ${portfolioId.value}")

        val snapshots = result.positionRisk.map { risk ->
            DailyRiskSnapshot(
                portfolioId = portfolioId,
                snapshotDate = date,
                instrumentId = risk.instrumentId,
                assetClass = risk.assetClass,
                quantity = risk.marketValue.abs(),
                marketPrice = risk.marketValue,
                delta = risk.delta,
                gamma = risk.gamma,
                vega = risk.vega,
                theta = result.greeks?.theta,
                rho = result.greeks?.rho,
            )
        }

        dailyRiskSnapshotRepository.saveAll(snapshots)

        val baseline = SodBaseline(
            portfolioId = portfolioId,
            baselineDate = date,
            snapshotType = snapshotType,
            createdAt = Instant.now(),
        )
        sodBaselineRepository.save(baseline)

        logger.info(
            "SOD snapshot created for portfolio {} on {} ({}, {} positions)",
            portfolioId.value, date, snapshotType, snapshots.size,
        )
    }

    suspend fun getBaselineStatus(portfolioId: PortfolioId, date: LocalDate): SodBaselineStatus {
        val baseline = sodBaselineRepository.findByPortfolioIdAndDate(portfolioId, date)
        return if (baseline != null) {
            SodBaselineStatus(
                exists = true,
                baselineDate = baseline.baselineDate.toString(),
                snapshotType = baseline.snapshotType,
                createdAt = baseline.createdAt,
            )
        } else {
            SodBaselineStatus(exists = false)
        }
    }

    suspend fun resetBaseline(portfolioId: PortfolioId, date: LocalDate) {
        sodBaselineRepository.deleteByPortfolioIdAndDate(portfolioId, date)
        logger.info("SOD baseline reset for portfolio {} on {}", portfolioId.value, date)
    }

    private suspend fun calculateFreshVaR(portfolioId: PortfolioId): ValuationResult? {
        logger.info("No cached VaR for {}, triggering fresh calculation for SOD snapshot", portfolioId.value)
        val request = VaRCalculationRequest(
            portfolioId = portfolioId,
            calculationType = CalculationType.PARAMETRIC,
            confidenceLevel = ConfidenceLevel.CL_95,
            requestedOutputs = setOf(ValuationOutput.VAR, ValuationOutput.EXPECTED_SHORTFALL, ValuationOutput.GREEKS),
        )
        return varCalculationService.calculateVaR(request, TriggerType.SCHEDULED)
    }
}
