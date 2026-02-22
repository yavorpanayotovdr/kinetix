package com.kinetix.notification.seed

import com.kinetix.notification.engine.RulesEngine
import com.kinetix.notification.model.*
import org.slf4j.LoggerFactory

class DevDataSeeder(
    private val rulesEngine: RulesEngine,
) {
    private val log = LoggerFactory.getLogger(DevDataSeeder::class.java)

    suspend fun seed() {
        val existing = rulesEngine.listRules()
        if (existing.isNotEmpty()) {
            log.info("Alert rules already present ({} rules), skipping seed", existing.size)
            return
        }

        log.info("Seeding {} notification rules", RULES.size)

        for (rule in RULES) {
            rulesEngine.addRule(rule)
        }

        log.info("Notification rule seeding complete")
    }

    companion object {
        val RULES: List<AlertRule> = listOf(
            AlertRule(
                id = "seed-rule-var-breach",
                name = "VaR Breach Alert",
                type = AlertType.VAR_BREACH,
                threshold = 1_000_000.0,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.CRITICAL,
                channels = listOf(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL),
            ),
            AlertRule(
                id = "seed-rule-pnl-threshold",
                name = "PnL Threshold Alert",
                type = AlertType.PNL_THRESHOLD,
                threshold = 500_000.0,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.WARNING,
                channels = listOf(DeliveryChannel.IN_APP),
            ),
            AlertRule(
                id = "seed-rule-risk-limit",
                name = "Risk Limit Alert",
                type = AlertType.RISK_LIMIT,
                threshold = 2_000_000.0,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.INFO,
                channels = listOf(DeliveryChannel.IN_APP),
            ),
        )
    }
}
