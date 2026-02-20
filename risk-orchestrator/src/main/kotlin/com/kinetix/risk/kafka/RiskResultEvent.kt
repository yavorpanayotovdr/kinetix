package com.kinetix.risk.kafka

import com.kinetix.risk.model.VaRResult
import kotlinx.serialization.Serializable

@Serializable
data class RiskResultEvent(
    val portfolioId: String,
    val calculationType: String,
    val confidenceLevel: String,
    val varValue: String,
    val expectedShortfall: String,
    val componentBreakdown: List<ComponentBreakdownEvent>,
    val calculatedAt: String,
) {
    companion object {
        fun from(result: VaRResult): RiskResultEvent = RiskResultEvent(
            portfolioId = result.portfolioId.value,
            calculationType = result.calculationType.name,
            confidenceLevel = result.confidenceLevel.name,
            varValue = result.varValue.toString(),
            expectedShortfall = result.expectedShortfall.toString(),
            componentBreakdown = result.componentBreakdown.map {
                ComponentBreakdownEvent(
                    assetClass = it.assetClass.name,
                    varContribution = it.varContribution.toString(),
                    percentageOfTotal = it.percentageOfTotal.toString(),
                )
            },
            calculatedAt = result.calculatedAt.toString(),
        )
    }
}

@Serializable
data class ComponentBreakdownEvent(
    val assetClass: String,
    val varContribution: String,
    val percentageOfTotal: String,
)
