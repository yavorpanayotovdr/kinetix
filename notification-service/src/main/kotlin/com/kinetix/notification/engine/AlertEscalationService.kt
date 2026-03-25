package com.kinetix.notification.engine

import com.kinetix.notification.delivery.DeliveryRouter
import com.kinetix.notification.model.DeliveryChannel
import com.kinetix.notification.model.Severity
import com.kinetix.notification.persistence.AlertEventRepository
import org.slf4j.LoggerFactory
import java.time.Instant

class AlertEscalationService(
    private val repository: AlertEventRepository,
    private val deliveryRouter: DeliveryRouter,
    private val escalationTimeoutMinutes: Long = 30,
) {
    private val logger = LoggerFactory.getLogger(AlertEscalationService::class.java)

    suspend fun processEscalations(now: Instant = Instant.now()) {
        val cutoff = now.minusSeconds(escalationTimeoutMinutes * 60)
        val overdue = repository.findAcknowledgedBefore(cutoff)

        for (alert in overdue) {
            val escalatedTo = escalationTargetFor(alert.severity)
            repository.escalate(alert.id, now, escalatedTo)

            val escalatedAlert = repository.findById(alert.id) ?: continue
            deliveryRouter.route(escalatedAlert, listOf(DeliveryChannel.EMAIL))
            logger.info(
                "Escalated alert={} severity={} to={} after {}min timeout",
                alert.id, alert.severity, escalatedTo, escalationTimeoutMinutes,
            )
        }
    }

    private fun escalationTargetFor(severity: Severity): String = when (severity) {
        Severity.WARNING -> "desk-head"
        Severity.CRITICAL -> "risk-manager,cro"
        Severity.INFO -> "desk-head"
    }
}
