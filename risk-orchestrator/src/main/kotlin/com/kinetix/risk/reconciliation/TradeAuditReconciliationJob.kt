package com.kinetix.risk.reconciliation

import com.kinetix.risk.client.AuditServiceClient
import com.kinetix.risk.client.ClientResponse
import com.kinetix.risk.client.PositionServiceInternalClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.coroutines.coroutineContext

class TradeAuditReconciliationJob(
    private val positionServiceClient: PositionServiceInternalClient,
    private val auditServiceClient: AuditServiceClient,
    private val clock: Clock = Clock.systemUTC(),
    private val windowDuration: Duration = Duration.ofHours(24),
) {
    private val logger = LoggerFactory.getLogger(TradeAuditReconciliationJob::class.java)

    suspend fun start() {
        while (coroutineContext.isActive) {
            val millisUntilNextRun = millisUntilNextScheduledRun()
            logger.info("Trade-audit reconciliation scheduled in {}ms", millisUntilNextRun)
            delay(millisUntilNextRun)
            runReconciliation()
        }
    }

    suspend fun runReconciliation(): ReconciliationResult? {
        val now = clock.instant()
        val since = now.minus(windowDuration)

        logger.info("Starting trade-audit reconciliation: window=[{}, {}]", since, now)

        val tradeCount = when (val response = positionServiceClient.countTradeEventsSince(since)) {
            is ClientResponse.Success -> response.value
            is ClientResponse.NotFound -> {
                logger.error("Trade count endpoint returned 404 — reconciliation aborted")
                return null
            }
        }

        val auditCount = when (val response = auditServiceClient.countAuditEventsSince(since)) {
            is ClientResponse.Success -> response.value
            is ClientResponse.NotFound -> {
                logger.error("Audit count endpoint returned 404 — reconciliation aborted")
                return null
            }
        }

        val matched = tradeCount == auditCount
        val result = ReconciliationResult(
            since = since,
            tradeCount = tradeCount,
            auditCount = auditCount,
            matched = matched,
        )

        if (matched) {
            logger.info(
                "Trade-audit reconciliation PASSED: tradeCount={} auditCount={} window={}h",
                tradeCount, auditCount, windowDuration.toHours(),
            )
        } else {
            logger.warn(
                "Trade-audit reconciliation MISMATCH: tradeCount={} auditCount={} delta={} window={}h",
                tradeCount, auditCount, tradeCount - auditCount, windowDuration.toHours(),
            )
        }

        return result
    }

    private fun millisUntilNextScheduledRun(): Long {
        val now = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC))
        val nextRun = now.toLocalDate()
            .atTime(SCHEDULED_HOUR, 0)
            .atZone(ZoneOffset.UTC)
            .let { scheduled ->
                if (scheduled.isAfter(now)) scheduled else scheduled.plusDays(1)
            }
        return Duration.between(now, nextRun).toMillis().coerceAtLeast(0L)
    }

    companion object {
        private const val SCHEDULED_HOUR = 4
    }
}
