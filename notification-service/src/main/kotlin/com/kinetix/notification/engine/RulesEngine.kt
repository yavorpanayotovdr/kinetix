package com.kinetix.notification.engine

import com.kinetix.notification.model.*
import com.kinetix.notification.persistence.AlertRuleRepository
import java.time.Instant
import java.util.UUID

class RulesEngine(private val repository: AlertRuleRepository) {

    suspend fun addRule(rule: AlertRule) {
        repository.save(rule)
    }

    suspend fun removeRule(ruleId: String): Boolean {
        return repository.deleteById(ruleId)
    }

    suspend fun listRules(): List<AlertRule> = repository.findAll()

    suspend fun evaluate(event: RiskResultEvent): List<AlertEvent> {
        return repository.findAll().filter { it.enabled }.mapNotNull { rule ->
            val currentValue = when (rule.type) {
                AlertType.VAR_BREACH -> event.varValue
                AlertType.PNL_THRESHOLD -> event.expectedShortfall
                AlertType.RISK_LIMIT -> event.varValue
            }
            val triggered = when (rule.operator) {
                ComparisonOperator.GREATER_THAN -> currentValue > rule.threshold
                ComparisonOperator.LESS_THAN -> currentValue < rule.threshold
                ComparisonOperator.EQUALS -> currentValue == rule.threshold
            }
            if (triggered) {
                AlertEvent(
                    id = UUID.randomUUID().toString(),
                    ruleId = rule.id,
                    ruleName = rule.name,
                    type = rule.type,
                    severity = rule.severity,
                    message = "${rule.name}: ${rule.type} ${rule.operator} ${rule.threshold} (current: $currentValue) for portfolio ${event.portfolioId}",
                    currentValue = currentValue,
                    threshold = rule.threshold,
                    portfolioId = event.portfolioId,
                    triggeredAt = Instant.now(),
                )
            } else {
                null
            }
        }
    }
}
