package com.kinetix.risk.schedule

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory

/**
 * Refreshes the risk_positions_flat materialised view after EOD promotion.
 * Called by EodPromotionService via the matViewRefresher hook — not on a timer.
 * Production uses CONCURRENT mode so reads are not blocked during refresh.
 */
class RiskPositionsFlatRefresher(private val db: Database) {

    private val logger = LoggerFactory.getLogger(RiskPositionsFlatRefresher::class.java)

    suspend fun refresh() {
        logger.info("Refreshing risk_positions_flat materialised view")
        newSuspendedTransaction(db = db) {
            exec("REFRESH MATERIALIZED VIEW CONCURRENTLY risk_positions_flat")
        }
        logger.info("risk_positions_flat refresh complete")
    }
}
