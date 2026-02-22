package com.kinetix.notification.seed

import com.kinetix.notification.engine.RulesEngine
import com.kinetix.notification.model.*
import com.kinetix.notification.persistence.AlertEventRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class DevDataSeeder(
    private val rulesEngine: RulesEngine,
    private val alertEventRepository: AlertEventRepository,
) {
    private val log = LoggerFactory.getLogger(DevDataSeeder::class.java)

    suspend fun seed() {
        val existing = rulesEngine.listRules()
        if (existing.isNotEmpty()) {
            log.info("Alert rules already present ({} rules), skipping seed", existing.size)
            return
        }

        log.info("Seeding {} notification rules and {} alert events", RULES.size, ALERT_EVENTS.size)

        for (rule in RULES) {
            rulesEngine.addRule(rule)
        }

        for (event in ALERT_EVENTS) {
            alertEventRepository.save(event)
        }

        log.info("Notification seeding complete")
    }

    companion object {
        private val BASE_TIME = Instant.parse("2026-02-22T10:00:00Z")
        private fun hoursAgo(h: Long): Instant = BASE_TIME.minus(h, ChronoUnit.HOURS)

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
            AlertRule(
                id = "seed-rule-concentration",
                name = "Concentration Risk Alert",
                type = AlertType.RISK_LIMIT,
                threshold = 1_500_000.0,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.WARNING,
                channels = listOf(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL),
            ),
            AlertRule(
                id = "seed-rule-daily-pnl",
                name = "Daily PnL Alert",
                type = AlertType.PNL_THRESHOLD,
                threshold = 250_000.0,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.INFO,
                channels = listOf(DeliveryChannel.IN_APP),
            ),
            AlertRule(
                id = "seed-rule-var-warning",
                name = "VaR Early Warning",
                type = AlertType.VAR_BREACH,
                threshold = 750_000.0,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.WARNING,
                channels = listOf(DeliveryChannel.IN_APP),
            ),
        )

        val ALERT_EVENTS: List<AlertEvent> = listOf(
            // CRITICAL events (4)
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-01".toByteArray()).toString(),
                ruleId = "seed-rule-var-breach",
                ruleName = "VaR Breach Alert",
                type = AlertType.VAR_BREACH,
                severity = Severity.CRITICAL,
                message = "VaR for portfolio equity-growth breached threshold: \$1,125,000 > \$1,000,000",
                currentValue = 1_125_000.0,
                threshold = 1_000_000.0,
                portfolioId = "equity-growth",
                triggeredAt = hoursAgo(2),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-02".toByteArray()).toString(),
                ruleId = "seed-rule-var-breach",
                ruleName = "VaR Breach Alert",
                type = AlertType.VAR_BREACH,
                severity = Severity.CRITICAL,
                message = "VaR for portfolio derivatives-book breached threshold: \$1,340,000 > \$1,000,000",
                currentValue = 1_340_000.0,
                threshold = 1_000_000.0,
                portfolioId = "derivatives-book",
                triggeredAt = hoursAgo(6),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-03".toByteArray()).toString(),
                ruleId = "seed-rule-var-breach",
                ruleName = "VaR Breach Alert",
                type = AlertType.VAR_BREACH,
                severity = Severity.CRITICAL,
                message = "VaR for portfolio tech-momentum breached threshold: \$1,210,000 > \$1,000,000",
                currentValue = 1_210_000.0,
                threshold = 1_000_000.0,
                portfolioId = "tech-momentum",
                triggeredAt = hoursAgo(18),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-04".toByteArray()).toString(),
                ruleId = "seed-rule-var-breach",
                ruleName = "VaR Breach Alert",
                type = AlertType.VAR_BREACH,
                severity = Severity.CRITICAL,
                message = "VaR for portfolio macro-hedge breached threshold: \$1,085,000 > \$1,000,000",
                currentValue = 1_085_000.0,
                threshold = 1_000_000.0,
                portfolioId = "macro-hedge",
                triggeredAt = hoursAgo(36),
            ),

            // WARNING events (6)
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-05".toByteArray()).toString(),
                ruleId = "seed-rule-pnl-threshold",
                ruleName = "PnL Threshold Alert",
                type = AlertType.PNL_THRESHOLD,
                severity = Severity.WARNING,
                message = "PnL for portfolio multi-asset exceeded threshold: \$583,200 > \$500,000",
                currentValue = 583_200.0,
                threshold = 500_000.0,
                portfolioId = "multi-asset",
                triggeredAt = hoursAgo(3),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-06".toByteArray()).toString(),
                ruleId = "seed-rule-concentration",
                ruleName = "Concentration Risk Alert",
                type = AlertType.RISK_LIMIT,
                severity = Severity.WARNING,
                message = "Concentration risk for portfolio tech-momentum: \$1,720,000 > \$1,500,000",
                currentValue = 1_720_000.0,
                threshold = 1_500_000.0,
                portfolioId = "tech-momentum",
                triggeredAt = hoursAgo(5),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-07".toByteArray()).toString(),
                ruleId = "seed-rule-var-warning",
                ruleName = "VaR Early Warning",
                type = AlertType.VAR_BREACH,
                severity = Severity.WARNING,
                message = "VaR approaching limit for portfolio balanced-income: \$812,000 > \$750,000",
                currentValue = 812_000.0,
                threshold = 750_000.0,
                portfolioId = "balanced-income",
                triggeredAt = hoursAgo(8),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-08".toByteArray()).toString(),
                ruleId = "seed-rule-concentration",
                ruleName = "Concentration Risk Alert",
                type = AlertType.RISK_LIMIT,
                severity = Severity.WARNING,
                message = "Concentration risk for portfolio derivatives-book: \$1,650,000 > \$1,500,000",
                currentValue = 1_650_000.0,
                threshold = 1_500_000.0,
                portfolioId = "derivatives-book",
                triggeredAt = hoursAgo(14),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-09".toByteArray()).toString(),
                ruleId = "seed-rule-pnl-threshold",
                ruleName = "PnL Threshold Alert",
                type = AlertType.PNL_THRESHOLD,
                severity = Severity.WARNING,
                message = "PnL for portfolio equity-growth exceeded threshold: \$545,800 > \$500,000",
                currentValue = 545_800.0,
                threshold = 500_000.0,
                portfolioId = "equity-growth",
                triggeredAt = hoursAgo(22),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-10".toByteArray()).toString(),
                ruleId = "seed-rule-var-warning",
                ruleName = "VaR Early Warning",
                type = AlertType.VAR_BREACH,
                severity = Severity.WARNING,
                message = "VaR approaching limit for portfolio emerging-markets: \$790,000 > \$750,000",
                currentValue = 790_000.0,
                threshold = 750_000.0,
                portfolioId = "emerging-markets",
                triggeredAt = hoursAgo(30),
            ),

            // INFO events (10)
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-11".toByteArray()).toString(),
                ruleId = "seed-rule-daily-pnl",
                ruleName = "Daily PnL Alert",
                type = AlertType.PNL_THRESHOLD,
                severity = Severity.INFO,
                message = "Daily PnL for portfolio equity-growth: \$312,400 > \$250,000",
                currentValue = 312_400.0,
                threshold = 250_000.0,
                portfolioId = "equity-growth",
                triggeredAt = hoursAgo(1),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-12".toByteArray()).toString(),
                ruleId = "seed-rule-daily-pnl",
                ruleName = "Daily PnL Alert",
                type = AlertType.PNL_THRESHOLD,
                severity = Severity.INFO,
                message = "Daily PnL for portfolio multi-asset: \$278,600 > \$250,000",
                currentValue = 278_600.0,
                threshold = 250_000.0,
                portfolioId = "multi-asset",
                triggeredAt = hoursAgo(4),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-13".toByteArray()).toString(),
                ruleId = "seed-rule-risk-limit",
                ruleName = "Risk Limit Alert",
                type = AlertType.RISK_LIMIT,
                severity = Severity.INFO,
                message = "Risk limit monitoring for portfolio fixed-income: \$2,150,000 > \$2,000,000",
                currentValue = 2_150_000.0,
                threshold = 2_000_000.0,
                portfolioId = "fixed-income",
                triggeredAt = hoursAgo(7),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-14".toByteArray()).toString(),
                ruleId = "seed-rule-daily-pnl",
                ruleName = "Daily PnL Alert",
                type = AlertType.PNL_THRESHOLD,
                severity = Severity.INFO,
                message = "Daily PnL for portfolio tech-momentum: \$289,300 > \$250,000",
                currentValue = 289_300.0,
                threshold = 250_000.0,
                portfolioId = "tech-momentum",
                triggeredAt = hoursAgo(10),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-15".toByteArray()).toString(),
                ruleId = "seed-rule-daily-pnl",
                ruleName = "Daily PnL Alert",
                type = AlertType.PNL_THRESHOLD,
                severity = Severity.INFO,
                message = "Daily PnL for portfolio derivatives-book: \$265,100 > \$250,000",
                currentValue = 265_100.0,
                threshold = 250_000.0,
                portfolioId = "derivatives-book",
                triggeredAt = hoursAgo(12),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-16".toByteArray()).toString(),
                ruleId = "seed-rule-risk-limit",
                ruleName = "Risk Limit Alert",
                type = AlertType.RISK_LIMIT,
                severity = Severity.INFO,
                message = "Risk limit monitoring for portfolio macro-hedge: \$2,080,000 > \$2,000,000",
                currentValue = 2_080_000.0,
                threshold = 2_000_000.0,
                portfolioId = "macro-hedge",
                triggeredAt = hoursAgo(16),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-17".toByteArray()).toString(),
                ruleId = "seed-rule-daily-pnl",
                ruleName = "Daily PnL Alert",
                type = AlertType.PNL_THRESHOLD,
                severity = Severity.INFO,
                message = "Daily PnL for portfolio balanced-income: \$258,900 > \$250,000",
                currentValue = 258_900.0,
                threshold = 250_000.0,
                portfolioId = "balanced-income",
                triggeredAt = hoursAgo(20),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-18".toByteArray()).toString(),
                ruleId = "seed-rule-daily-pnl",
                ruleName = "Daily PnL Alert",
                type = AlertType.PNL_THRESHOLD,
                severity = Severity.INFO,
                message = "Daily PnL for portfolio emerging-markets: \$271,500 > \$250,000",
                currentValue = 271_500.0,
                threshold = 250_000.0,
                portfolioId = "emerging-markets",
                triggeredAt = hoursAgo(28),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-19".toByteArray()).toString(),
                ruleId = "seed-rule-risk-limit",
                ruleName = "Risk Limit Alert",
                type = AlertType.RISK_LIMIT,
                severity = Severity.INFO,
                message = "Risk limit monitoring for portfolio equity-growth: \$2,230,000 > \$2,000,000",
                currentValue = 2_230_000.0,
                threshold = 2_000_000.0,
                portfolioId = "equity-growth",
                triggeredAt = hoursAgo(38),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-20".toByteArray()).toString(),
                ruleId = "seed-rule-daily-pnl",
                ruleName = "Daily PnL Alert",
                type = AlertType.PNL_THRESHOLD,
                severity = Severity.INFO,
                message = "Daily PnL for portfolio macro-hedge: \$263,700 > \$250,000",
                currentValue = 263_700.0,
                threshold = 250_000.0,
                portfolioId = "macro-hedge",
                triggeredAt = hoursAgo(44),
            ),
        )
    }
}
