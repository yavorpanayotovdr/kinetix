package com.kinetix.notification.engine

import org.slf4j.LoggerFactory
import java.time.Instant

class ScheduledAlertEscalation(private val escalationService: AlertEscalationService) {
    private val logger = LoggerFactory.getLogger(ScheduledAlertEscalation::class.java)

    suspend fun tick() {
        val now = Instant.now()
        logger.debug("Running escalation check at {}", now)
        escalationService.processEscalations(now)
    }
}
