package com.kinetix.notification.model

import java.time.Instant

enum class AlertType { VAR_BREACH, PNL_THRESHOLD, RISK_LIMIT }
enum class Severity { INFO, WARNING, CRITICAL }
enum class ComparisonOperator { GREATER_THAN, LESS_THAN, EQUALS }
enum class DeliveryChannel { IN_APP, EMAIL, WEBHOOK }

data class AlertRule(
    val id: String,
    val name: String,
    val type: AlertType,
    val threshold: Double,
    val operator: ComparisonOperator,
    val severity: Severity,
    val channels: List<DeliveryChannel>,
    val enabled: Boolean = true,
)

data class AlertEvent(
    val id: String,
    val ruleId: String,
    val ruleName: String,
    val type: AlertType,
    val severity: Severity,
    val message: String,
    val currentValue: Double,
    val threshold: Double,
    val portfolioId: String,
    val triggeredAt: Instant,
)
