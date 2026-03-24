package com.kinetix.position.fix

import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant

/**
 * Compares internal position quantities against a prime broker statement
 * and produces a [PrimeBrokerReconciliation] result.
 *
 * Auto-resolve threshold: breaks < 1 unit are treated as rounding artifacts.
 * Material breaks (>= 1 unit) are included in the result for investigation.
 */
class PrimeBrokerReconciliationService {

    private val logger = LoggerFactory.getLogger(PrimeBrokerReconciliationService::class.java)

    companion object {
        private val AUTO_RESOLVE_THRESHOLD = BigDecimal("1.0")
    }

    fun reconcile(
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

            logger.warn(
                "Reconciliation break: book={}, instrument={}, internal={}, pb={}, break={}, notional={}",
                bookId, instrumentId, internalQty, pbQty, breakQty, breakNotional,
            )

            materialBreaks.add(
                ReconciliationBreak(
                    instrumentId = instrumentId,
                    internalQty = internalQty,
                    primeBrokerQty = pbQty,
                    breakQty = internalQty - pbQty,
                    breakNotional = breakNotional,
                )
            )
        }

        val status = if (materialBreaks.isEmpty()) "CLEAN" else "BREAKS_FOUND"
        val totalPositions = internalPositions.size
        val matchedCount = totalPositions - materialBreaks.count { internalPositions.containsKey(it.instrumentId) }

        return PrimeBrokerReconciliation(
            reconciliationDate = date,
            bookId = bookId,
            status = status,
            totalPositions = totalPositions,
            matchedCount = matchedCount,
            breakCount = materialBreaks.size,
            breaks = materialBreaks,
            reconciledAt = reconciledAt,
        )
    }
}
