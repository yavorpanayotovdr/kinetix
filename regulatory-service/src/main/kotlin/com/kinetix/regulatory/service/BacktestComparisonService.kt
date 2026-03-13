package com.kinetix.regulatory.service

import com.kinetix.regulatory.model.BacktestComparison
import com.kinetix.regulatory.model.BacktestConfig
import com.kinetix.regulatory.persistence.BacktestResultRepository

class BacktestComparisonService(
    private val repository: BacktestResultRepository,
) {
    suspend fun compare(baseId: String, targetId: String): BacktestComparison {
        val base = repository.findById(baseId)
            ?: throw IllegalArgumentException("Base backtest result not found: $baseId")
        val target = repository.findById(targetId)
            ?: throw IllegalArgumentException("Target backtest result not found: $targetId")

        return BacktestComparison(
            baseConfig = BacktestConfig(base.calculationType, base.confidenceLevel, base.totalDays),
            targetConfig = BacktestConfig(target.calculationType, target.confidenceLevel, target.totalDays),
            baseViolationCount = base.violationCount,
            targetViolationCount = target.violationCount,
            violationCountDiff = target.violationCount - base.violationCount,
            baseViolationRate = base.violationRate,
            targetViolationRate = target.violationRate,
            violationRateDiff = target.violationRate - base.violationRate,
            baseKupiecPValue = base.kupiecPValue,
            targetKupiecPValue = target.kupiecPValue,
            baseChristoffersenPValue = base.christoffersenPValue,
            targetChristoffersenPValue = target.christoffersenPValue,
            baseTrafficLightZone = base.trafficLightZone,
            targetTrafficLightZone = target.trafficLightZone,
            trafficLightChanged = base.trafficLightZone != target.trafficLightZone,
        )
    }
}
