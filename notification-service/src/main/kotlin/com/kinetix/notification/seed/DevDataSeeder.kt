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
                threshold = 15_000_000.0,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.CRITICAL,
                channels = listOf(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL),
            ),
            AlertRule(
                id = "seed-rule-pnl-threshold",
                name = "PnL Threshold Alert",
                type = AlertType.PNL_THRESHOLD,
                threshold = 5_000_000.0,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.WARNING,
                channels = listOf(DeliveryChannel.IN_APP),
            ),
            AlertRule(
                id = "seed-rule-risk-limit",
                name = "Risk Limit Alert",
                type = AlertType.RISK_LIMIT,
                threshold = 25_000_000.0,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.INFO,
                channels = listOf(DeliveryChannel.IN_APP),
            ),
            AlertRule(
                id = "seed-rule-concentration",
                name = "Concentration Risk Alert",
                type = AlertType.RISK_LIMIT,
                threshold = 20_000_000.0,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.WARNING,
                channels = listOf(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL),
            ),
            AlertRule(
                id = "seed-rule-daily-pnl",
                name = "Daily PnL Alert",
                type = AlertType.PNL_THRESHOLD,
                threshold = 3_000_000.0,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.INFO,
                channels = listOf(DeliveryChannel.IN_APP),
            ),
            AlertRule(
                id = "seed-rule-var-warning",
                name = "VaR Early Warning",
                type = AlertType.VAR_BREACH,
                threshold = 10_000_000.0,
                operator = ComparisonOperator.GREATER_THAN,
                severity = Severity.WARNING,
                channels = listOf(DeliveryChannel.IN_APP),
            ),
        )

        val ALERT_EVENTS: List<AlertEvent> = listOf(
            // CRITICAL events (4) — VaR breach threshold 15M (15x scale)
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-01".toByteArray()).toString(),
                ruleId = "seed-rule-var-breach",
                ruleName = "VaR Breach Alert",
                type = AlertType.VAR_BREACH,
                severity = Severity.CRITICAL,
                message = "VaR for portfolio equity-growth breached threshold: \$16,875,000 > \$15,000,000",
                currentValue = 16_875_000.0,
                threshold = 15_000_000.0,
                bookId = "equity-growth",
                triggeredAt = hoursAgo(2),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-02".toByteArray()).toString(),
                ruleId = "seed-rule-var-breach",
                ruleName = "VaR Breach Alert",
                type = AlertType.VAR_BREACH,
                severity = Severity.CRITICAL,
                message = "VaR for portfolio derivatives-book breached threshold: \$20,100,000 > \$15,000,000",
                currentValue = 20_100_000.0,
                threshold = 15_000_000.0,
                bookId = "derivatives-book",
                triggeredAt = hoursAgo(6),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-03".toByteArray()).toString(),
                ruleId = "seed-rule-var-breach",
                ruleName = "VaR Breach Alert",
                type = AlertType.VAR_BREACH,
                severity = Severity.CRITICAL,
                message = "VaR for portfolio tech-momentum breached threshold: \$18,150,000 > \$15,000,000",
                currentValue = 18_150_000.0,
                threshold = 15_000_000.0,
                bookId = "tech-momentum",
                triggeredAt = hoursAgo(18),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-04".toByteArray()).toString(),
                ruleId = "seed-rule-var-breach",
                ruleName = "VaR Breach Alert",
                type = AlertType.VAR_BREACH,
                severity = Severity.CRITICAL,
                message = "VaR for portfolio macro-hedge breached threshold: \$16,275,000 > \$15,000,000",
                currentValue = 16_275_000.0,
                threshold = 15_000_000.0,
                bookId = "macro-hedge",
                triggeredAt = hoursAgo(36),
            ),

            // WARNING events (6) — PnL threshold 5M (10x), concentration 20M (~13.3x), VaR warning 10M (~13.3x)
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-05".toByteArray()).toString(),
                ruleId = "seed-rule-pnl-threshold",
                ruleName = "PnL Threshold Alert",
                type = AlertType.PNL_THRESHOLD,
                severity = Severity.WARNING,
                message = "PnL for portfolio multi-asset exceeded threshold: \$5,832,000 > \$5,000,000",
                currentValue = 5_832_000.0,
                threshold = 5_000_000.0,
                bookId = "multi-asset",
                triggeredAt = hoursAgo(3),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-06".toByteArray()).toString(),
                ruleId = "seed-rule-concentration",
                ruleName = "Concentration Risk Alert",
                type = AlertType.RISK_LIMIT,
                severity = Severity.WARNING,
                message = "Concentration risk for portfolio tech-momentum: \$22,933,000 > \$20,000,000",
                currentValue = 22_933_000.0,
                threshold = 20_000_000.0,
                bookId = "tech-momentum",
                triggeredAt = hoursAgo(5),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-07".toByteArray()).toString(),
                ruleId = "seed-rule-var-warning",
                ruleName = "VaR Early Warning",
                type = AlertType.VAR_BREACH,
                severity = Severity.WARNING,
                message = "VaR approaching limit for portfolio balanced-income: \$10,827,000 > \$10,000,000",
                currentValue = 10_827_000.0,
                threshold = 10_000_000.0,
                bookId = "balanced-income",
                triggeredAt = hoursAgo(8),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-08".toByteArray()).toString(),
                ruleId = "seed-rule-concentration",
                ruleName = "Concentration Risk Alert",
                type = AlertType.RISK_LIMIT,
                severity = Severity.WARNING,
                message = "Concentration risk for portfolio derivatives-book: \$22,000,000 > \$20,000,000",
                currentValue = 22_000_000.0,
                threshold = 20_000_000.0,
                bookId = "derivatives-book",
                triggeredAt = hoursAgo(14),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-09".toByteArray()).toString(),
                ruleId = "seed-rule-pnl-threshold",
                ruleName = "PnL Threshold Alert",
                type = AlertType.PNL_THRESHOLD,
                severity = Severity.WARNING,
                message = "PnL for portfolio equity-growth exceeded threshold: \$5,458,000 > \$5,000,000",
                currentValue = 5_458_000.0,
                threshold = 5_000_000.0,
                bookId = "equity-growth",
                triggeredAt = hoursAgo(22),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-10".toByteArray()).toString(),
                ruleId = "seed-rule-var-warning",
                ruleName = "VaR Early Warning",
                type = AlertType.VAR_BREACH,
                severity = Severity.WARNING,
                message = "VaR approaching limit for portfolio emerging-markets: \$10,533,000 > \$10,000,000",
                currentValue = 10_533_000.0,
                threshold = 10_000_000.0,
                bookId = "emerging-markets",
                triggeredAt = hoursAgo(30),
            ),

            // INFO events (10) — daily PnL threshold 3M (12x), risk limit 25M (12.5x)
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-11".toByteArray()).toString(),
                ruleId = "seed-rule-daily-pnl",
                ruleName = "Daily PnL Alert",
                type = AlertType.PNL_THRESHOLD,
                severity = Severity.INFO,
                message = "Daily PnL for portfolio equity-growth: \$3,748,800 > \$3,000,000",
                currentValue = 3_748_800.0,
                threshold = 3_000_000.0,
                bookId = "equity-growth",
                triggeredAt = hoursAgo(1),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-12".toByteArray()).toString(),
                ruleId = "seed-rule-daily-pnl",
                ruleName = "Daily PnL Alert",
                type = AlertType.PNL_THRESHOLD,
                severity = Severity.INFO,
                message = "Daily PnL for portfolio multi-asset: \$3,343,200 > \$3,000,000",
                currentValue = 3_343_200.0,
                threshold = 3_000_000.0,
                bookId = "multi-asset",
                triggeredAt = hoursAgo(4),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-13".toByteArray()).toString(),
                ruleId = "seed-rule-risk-limit",
                ruleName = "Risk Limit Alert",
                type = AlertType.RISK_LIMIT,
                severity = Severity.INFO,
                message = "Risk limit monitoring for portfolio fixed-income: \$26,875,000 > \$25,000,000",
                currentValue = 26_875_000.0,
                threshold = 25_000_000.0,
                bookId = "fixed-income",
                triggeredAt = hoursAgo(7),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-14".toByteArray()).toString(),
                ruleId = "seed-rule-daily-pnl",
                ruleName = "Daily PnL Alert",
                type = AlertType.PNL_THRESHOLD,
                severity = Severity.INFO,
                message = "Daily PnL for portfolio tech-momentum: \$3,471,600 > \$3,000,000",
                currentValue = 3_471_600.0,
                threshold = 3_000_000.0,
                bookId = "tech-momentum",
                triggeredAt = hoursAgo(10),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-15".toByteArray()).toString(),
                ruleId = "seed-rule-daily-pnl",
                ruleName = "Daily PnL Alert",
                type = AlertType.PNL_THRESHOLD,
                severity = Severity.INFO,
                message = "Daily PnL for portfolio derivatives-book: \$3,181,200 > \$3,000,000",
                currentValue = 3_181_200.0,
                threshold = 3_000_000.0,
                bookId = "derivatives-book",
                triggeredAt = hoursAgo(12),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-16".toByteArray()).toString(),
                ruleId = "seed-rule-risk-limit",
                ruleName = "Risk Limit Alert",
                type = AlertType.RISK_LIMIT,
                severity = Severity.INFO,
                message = "Risk limit monitoring for portfolio macro-hedge: \$26,000,000 > \$25,000,000",
                currentValue = 26_000_000.0,
                threshold = 25_000_000.0,
                bookId = "macro-hedge",
                triggeredAt = hoursAgo(16),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-17".toByteArray()).toString(),
                ruleId = "seed-rule-daily-pnl",
                ruleName = "Daily PnL Alert",
                type = AlertType.PNL_THRESHOLD,
                severity = Severity.INFO,
                message = "Daily PnL for portfolio balanced-income: \$3,106,800 > \$3,000,000",
                currentValue = 3_106_800.0,
                threshold = 3_000_000.0,
                bookId = "balanced-income",
                triggeredAt = hoursAgo(20),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-18".toByteArray()).toString(),
                ruleId = "seed-rule-daily-pnl",
                ruleName = "Daily PnL Alert",
                type = AlertType.PNL_THRESHOLD,
                severity = Severity.INFO,
                message = "Daily PnL for portfolio emerging-markets: \$3,258,000 > \$3,000,000",
                currentValue = 3_258_000.0,
                threshold = 3_000_000.0,
                bookId = "emerging-markets",
                triggeredAt = hoursAgo(28),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-19".toByteArray()).toString(),
                ruleId = "seed-rule-risk-limit",
                ruleName = "Risk Limit Alert",
                type = AlertType.RISK_LIMIT,
                severity = Severity.INFO,
                message = "Risk limit monitoring for portfolio equity-growth: \$27,875,000 > \$25,000,000",
                currentValue = 27_875_000.0,
                threshold = 25_000_000.0,
                bookId = "equity-growth",
                triggeredAt = hoursAgo(38),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-20".toByteArray()).toString(),
                ruleId = "seed-rule-daily-pnl",
                ruleName = "Daily PnL Alert",
                type = AlertType.PNL_THRESHOLD,
                severity = Severity.INFO,
                message = "Daily PnL for portfolio macro-hedge: \$3,164,400 > \$3,000,000",
                currentValue = 3_164_400.0,
                threshold = 3_000_000.0,
                bookId = "macro-hedge",
                triggeredAt = hoursAgo(44),
            ),

            // Limit breach alerts
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-21".toByteArray()).toString(),
                ruleId = "seed-rule-risk-limit",
                ruleName = "Risk Limit Alert",
                type = AlertType.RISK_LIMIT,
                severity = Severity.CRITICAL,
                message = "Notional limit breached for portfolio derivatives-book: 104% of limit utilised",
                currentValue = 26_000_000.0,
                threshold = 25_000_000.0,
                bookId = "derivatives-book",
                triggeredAt = hoursAgo(9),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-22".toByteArray()).toString(),
                ruleId = "seed-rule-concentration",
                ruleName = "Concentration Risk Alert",
                type = AlertType.RISK_LIMIT,
                severity = Severity.CRITICAL,
                message = "Concentration limit breached for portfolio tech-momentum: 116% of limit utilised",
                currentValue = 23_200_000.0,
                threshold = 20_000_000.0,
                bookId = "tech-momentum",
                triggeredAt = hoursAgo(11),
            ),

            // VaR escalation sequence for derivatives-book over 3 hours
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-23".toByteArray()).toString(),
                ruleId = "seed-rule-var-warning",
                ruleName = "VaR Early Warning",
                type = AlertType.VAR_BREACH,
                severity = Severity.INFO,
                message = "VaR approaching limit for portfolio derivatives-book: \$14,200,000 > \$10,000,000",
                currentValue = 14_200_000.0,
                threshold = 10_000_000.0,
                bookId = "derivatives-book",
                triggeredAt = hoursAgo(8),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-24".toByteArray()).toString(),
                ruleId = "seed-rule-var-warning",
                ruleName = "VaR Early Warning",
                type = AlertType.VAR_BREACH,
                severity = Severity.WARNING,
                message = "VaR at 92% of limit for portfolio derivatives-book: \$13,800,000 approaching \$15,000,000",
                currentValue = 13_800_000.0,
                threshold = 15_000_000.0,
                bookId = "derivatives-book",
                triggeredAt = hoursAgo(7),
            ),
            AlertEvent(
                id = UUID.nameUUIDFromBytes("seed-alert-25".toByteArray()).toString(),
                ruleId = "seed-rule-var-breach",
                ruleName = "VaR Breach Alert",
                type = AlertType.VAR_BREACH,
                severity = Severity.CRITICAL,
                message = "VaR limit breached for portfolio derivatives-book: \$20,100,000 > \$15,000,000",
                currentValue = 20_100_000.0,
                threshold = 15_000_000.0,
                bookId = "derivatives-book",
                triggeredAt = hoursAgo(6),
            ),
        )
    }
}
