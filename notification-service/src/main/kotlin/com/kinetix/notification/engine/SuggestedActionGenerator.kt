package com.kinetix.notification.engine

import com.kinetix.common.kafka.events.PositionBreakdownItem
import com.kinetix.common.kafka.events.RiskResultEvent
import com.kinetix.notification.model.AlertRule
import com.kinetix.notification.model.AlertType
import kotlin.math.abs
import kotlin.math.roundToInt

class SuggestedActionGenerator {

    fun generate(
        rule: AlertRule,
        event: RiskResultEvent,
        contributors: List<PositionBreakdownItem>,
    ): String? {
        if (rule.type != AlertType.VAR_BREACH && rule.type != AlertType.RISK_LIMIT) return null
        if (contributors.isEmpty()) return null

        val currentVar = event.varValue.toDoubleOrNull() ?: return null
        val targetVar = rule.threshold
        val varExcess = currentVar - targetVar
        if (varExcess <= 0) return null

        val topContributor = contributors
            .sortedByDescending { abs(it.varContribution.toDoubleOrNull() ?: 0.0) }
            .firstOrNull() ?: return null

        val hasNonLinear = topContributor.gamma != null || topContributor.vega != null
        val gammaVal = topContributor.gamma?.toDoubleOrNull() ?: 0.0
        val vegaVal = topContributor.vega?.toDoubleOrNull() ?: 0.0

        // For options positions with significant gamma/vega, don't give linear estimates
        if (hasNonLinear && (abs(gammaVal) > 0.01 || abs(vegaVal) > 1.0)) {
            val pctOfTotal = topContributor.percentageOfTotal.toDoubleOrNull()?.roundToInt() ?: 0
            return buildString {
                append("Book VaR exceeds limit by $${formatAmount(varExcess)}. ")
                append("Top contributor: ${topContributor.instrumentId} ($pctOfTotal%). ")
                append("Non-linear position — use What-If for accurate sizing.")
            }
        }

        val topVarContribution = topContributor.varContribution.toDoubleOrNull() ?: return null
        val topPctOfTotal = topContributor.percentageOfTotal.toDoubleOrNull()?.roundToInt() ?: 0

        // Linear approximation: what % reduction in the top position would bring VaR within limit?
        val reductionNeeded = if (topVarContribution != 0.0) {
            (varExcess / topVarContribution * 100).roundToInt().coerceIn(1, 100)
        } else {
            return null
        }

        return buildString {
            append("Book VaR exceeds limit by $${formatAmount(varExcess)}. ")
            append("Top contributor: ${topContributor.instrumentId} ($topPctOfTotal%). ")
            append("Reducing ${topContributor.instrumentId} by ~$reductionNeeded% would bring estimated VaR within limit.")
        }
    }

    private fun formatAmount(value: Double): String {
        return when {
            abs(value) >= 1_000_000 -> "${(value / 1_000_000).let { "%.1f".format(it) }}M"
            abs(value) >= 1_000 -> "${(value / 1_000).let { "%.0f".format(it) }}K"
            else -> "%.0f".format(value)
        }
    }
}
