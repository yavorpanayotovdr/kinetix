package com.kinetix.notification.engine

import com.kinetix.common.kafka.events.RiskResultEvent
import com.kinetix.notification.model.*
import com.kinetix.notification.persistence.AlertRuleRepository
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class RulesEngine(
    private val repository: AlertRuleRepository,
    private val meterRegistry: MeterRegistry? = null,
) {

    private val logger = LoggerFactory.getLogger(RulesEngine::class.java)

    suspend fun addRule(rule: AlertRule) {
        repository.save(rule)
    }

    suspend fun removeRule(ruleId: String): Boolean {
        return repository.deleteById(ruleId)
    }

    suspend fun listRules(): List<AlertRule> = repository.findAll()

    suspend fun evaluate(event: RiskResultEvent): List<AlertEvent> {
        val rules = repository.findAll().filter { it.enabled }
        val alerts = rules.mapNotNull { rule ->
            meterRegistry?.counter(
                "notification_rules_evaluated_total",
                "alert_type", rule.type.name,
                "book_id", event.bookId,
            )?.increment()

            val currentValue = when (rule.type) {
                AlertType.VAR_BREACH -> event.varValue.toDoubleOrNull() ?: 0.0
                AlertType.PNL_THRESHOLD -> event.expectedShortfall.toDoubleOrNull() ?: 0.0
                AlertType.RISK_LIMIT -> event.varValue.toDoubleOrNull() ?: 0.0
            }
            val triggered = when (rule.operator) {
                ComparisonOperator.GREATER_THAN -> currentValue > rule.threshold
                ComparisonOperator.LESS_THAN -> currentValue < rule.threshold
                ComparisonOperator.EQUALS -> currentValue == rule.threshold
            }
            if (triggered) {
                meterRegistry?.counter(
                    "notification_alerts_triggered_total",
                    "alert_type", rule.type.name,
                    "severity", rule.severity.name,
                )?.increment()

                AlertEvent(
                    id = UUID.randomUUID().toString(),
                    ruleId = rule.id,
                    ruleName = rule.name,
                    type = rule.type,
                    severity = rule.severity,
                    message = "${rule.name}: ${rule.type} ${rule.operator} ${rule.threshold} (current: $currentValue) for book ${event.bookId}",
                    currentValue = currentValue,
                    threshold = rule.threshold,
                    bookId = event.bookId,
                    triggeredAt = Instant.now(),
                )
            } else {
                null
            }
        }

        if (alerts.isEmpty()) {
            logger.debug("No alerts triggered for book={}, rules evaluated={}", event.bookId, rules.size)
        }

        return alerts
    }
}
