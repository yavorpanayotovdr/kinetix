package com.kinetix.risk.kafka

import com.kinetix.risk.model.ValuationResult
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
    val correlationId: String? = null,
) {
    companion object {
        fun from(result: ValuationResult, correlationId: String? = null): RiskResultEvent = RiskResultEvent(
            portfolioId = result.portfolioId.value,
            calculationType = result.calculationType.name,
            confidenceLevel = result.confidenceLevel.name,
            varValue = (result.varValue ?: 0.0).toString(),
            expectedShortfall = (result.expectedShortfall ?: 0.0).toString(),
            componentBreakdown = result.componentBreakdown.map {
                ComponentBreakdownEvent(
                    assetClass = it.assetClass.name,
                    varContribution = it.varContribution.toString(),
                    percentageOfTotal = it.percentageOfTotal.toString(),
                )
            },
            calculatedAt = result.calculatedAt.toString(),
            correlationId = correlationId,
        )
    }
}

@Serializable
data class ComponentBreakdownEvent(
    val assetClass: String,
    val varContribution: String,
    val percentageOfTotal: String,
)
