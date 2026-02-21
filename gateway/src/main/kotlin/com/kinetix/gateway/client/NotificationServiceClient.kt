package com.kinetix.gateway.client

import java.time.Instant

data class AlertRuleItem(
    val id: String,
    val name: String,
    val type: String,
    val threshold: Double,
    val operator: String,
    val severity: String,
    val channels: List<String>,
    val enabled: Boolean,
)

data class AlertEventItem(
    val id: String,
    val ruleId: String,
    val ruleName: String,
    val type: String,
    val severity: String,
    val message: String,
    val currentValue: Double,
    val threshold: Double,
    val portfolioId: String,
    val triggeredAt: Instant,
)

data class CreateAlertRuleParams(
    val name: String,
    val type: String,
    val threshold: Double,
    val operator: String,
    val severity: String,
    val channels: List<String>,
)

interface NotificationServiceClient {
    suspend fun listRules(): List<AlertRuleItem>
    suspend fun createRule(params: CreateAlertRuleParams): AlertRuleItem
    suspend fun deleteRule(ruleId: String): Boolean
    suspend fun listAlerts(limit: Int = 50): List<AlertEventItem>
}
