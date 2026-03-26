package com.kinetix.position.fix

import com.kinetix.position.kafka.ReconciliationAlertPublisher
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant

/**
 * Compares internal position quantities against a prime broker statement
 * and produces a [PrimeBrokerReconciliation] result.
 *
 * Auto-resolve threshold: breaks < 1 unit are treated as rounding artifacts.
 * Material breaks (>= 1 unit) are included in the result for investigation.
 *
 * After reconciliation, if any critical breaks (notional > $10,000) are found,
 * a RECONCILIATION_BREAK alert is published via [alertPublisher].
 */
class PrimeBrokerReconciliationService(
    private val alertPublisher: ReconciliationAlertPublisher? = null,
) {

    private val logger = LoggerFactory.getLogger(PrimeBrokerReconciliationService::class.java)

    companion object {
        private val AUTO_RESOLVE_THRESHOLD = BigDecimal("1.0")
        private val CRITICAL_NOTIONAL_THRESHOLD = BigDecimal("10000")
    }

    suspend fun reconcile(
        bookId: String,
        date: String,
        internalPositions: Map<String, BigDecimal>,
        pbPositions: Map<String, PrimeBrokerPosition>,
        reconciledAt: Instant,
    ): PrimeBrokerReconciliation {
        val allInstruments = internalPositions.keys + pbPositions.keys

        val materialBreaks = mutableListOf<ReconciliationBreak>()

        for (instrumentId in allInstruments) {
            val internalQty = internalPositions[instrumentId] ?: BigDecimal.ZERO
            val pbEntry = pbPositions[instrumentId]
            val pbQty = pbEntry?.quantity ?: BigDecimal.ZERO
            val breakQty = (internalQty - pbQty).abs()

            if (breakQty < AUTO_RESOLVE_THRESHOLD) {
                // Rounding artifact — auto-resolved
                continue
            }

            val price = pbEntry?.price ?: BigDecimal.ZERO
            val breakNotional = breakQty * price
            val severity = if (breakNotional >= CRITICAL_NOTIONAL_THRESHOLD)
                ReconciliationBreakSeverity.CRITICAL
            else
                ReconciliationBreakSeverity.NORMAL

            logger.warn(
                "Reconciliation break: book={}, instrument={}, internal={}, pb={}, break={}, notional={}, severity={}",
                bookId, instrumentId, internalQty, pbQty, breakQty, breakNotional, severity,
            )

            materialBreaks.add(
                ReconciliationBreak(
                    instrumentId = instrumentId,
                    internalQty = internalQty,
                    primeBrokerQty = pbQty,
                    breakQty = internalQty - pbQty,
                    breakNotional = breakNotional,
                    severity = severity,
                )
            )
        }

        val status = if (materialBreaks.isEmpty()) "CLEAN" else "BREAKS_FOUND"
        val totalPositions = internalPositions.size
        val matchedCount = totalPositions - materialBreaks.count { internalPositions.containsKey(it.instrumentId) }

        val reconciliation = PrimeBrokerReconciliation(
            reconciliationDate = date,
            bookId = bookId,
            status = status,
            totalPositions = totalPositions,
            matchedCount = matchedCount,
            breakCount = materialBreaks.size,
            breaks = materialBreaks,
            reconciledAt = reconciledAt,
        )

        val criticalBreaks = materialBreaks.filter { it.severity == ReconciliationBreakSeverity.CRITICAL }
        if (criticalBreaks.isNotEmpty()) {
            alertPublisher?.publishBreakAlert(reconciliation)
        }

        return reconciliation
    }
}
