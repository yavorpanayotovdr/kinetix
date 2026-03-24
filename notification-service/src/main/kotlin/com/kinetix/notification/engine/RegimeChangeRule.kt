package com.kinetix.notification.engine

import com.kinetix.common.kafka.events.MarketRegimeEvent
import com.kinetix.notification.model.AlertEvent
import com.kinetix.notification.model.AlertStatus
import com.kinetix.notification.model.AlertType
import com.kinetix.notification.model.Severity
import java.time.Instant
import java.util.UUID

/**
 * Evaluates a [MarketRegimeEvent] and produces a [AlertEvent] when the regime
 * transitions to a state that warrants operator attention.
 *
 * Severity mapping:
 *   CRISIS       → CRITICAL
 *   ELEVATED_VOL → WARNING
 *   RECOVERY     → INFO
 *   NORMAL       → no alert (returns null)
 */
class RegimeChangeRule {

    fun evaluate(event: MarketRegimeEvent): AlertEvent? {
        val severity = when (event.regime) {
            "CRISIS" -> Severity.CRITICAL
            "ELEVATED_VOL" -> Severity.WARNING
            "RECOVERY" -> Severity.INFO
            else -> return null
        }

        val message = buildString {
            append("Market regime changed from ${event.previousRegime} to ${event.regime}. ")
            append("Effective VaR method: ${event.effectiveCalculationType} / ${event.effectiveConfidenceLevel}")
            append(" (${event.effectiveTimeHorizonDays}d, ${event.effectiveCorrelationMethod}).")
            if (event.degradedInputs) append(" Warning: degraded signal inputs.")
        }

        return AlertEvent(
            id = UUID.randomUUID().toString(),
            ruleId = "REGIME_CHANGE",
            ruleName = "Market Regime Change",
            type = AlertType.REGIME_CHANGE,
            severity = severity,
            message = message,
            currentValue = 0.0,
            threshold = 0.0,
            bookId = "GLOBAL",
            triggeredAt = Instant.now(),
            status = AlertStatus.TRIGGERED,
            correlationId = event.correlationId,
        )
    }
}
