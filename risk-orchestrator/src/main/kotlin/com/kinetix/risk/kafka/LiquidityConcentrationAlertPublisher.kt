package com.kinetix.risk.kafka

import com.kinetix.common.model.LiquidityRiskResult

/**
 * Publishes an alert when portfolio-level liquidity concentration risk is detected.
 * Implementations emit a [com.kinetix.common.kafka.events.RiskResultEvent] to the
 * `risk.results` topic so the notification-service's LiquidityConcentrationExtractor
 * can evaluate configured alert rules.
 */
interface LiquidityConcentrationAlertPublisher {
    suspend fun publishConcentrationAlert(result: LiquidityRiskResult)
}
