package com.kinetix.position.model

import java.math.BigDecimal

/**
 * Exposure breakdown for a single netting set (ISDA, GMRA, or null for un-netted trades).
 * Net exposure is floored at zero: a netting set cannot have negative exposure
 * (negative means we owe the counterparty, which reduces our credit risk but is not negative exposure).
 */
data class NettingSetExposure(
    val nettingSetId: String?,
    val netExposure: BigDecimal,
    val positionCount: Int,
)

data class CounterpartyExposure(
    val counterpartyId: String,
    /**
     * Sum of per-netting-set net exposures, each floored at zero.
     * This is the netting-set-aware net exposure — only positions within
     * the same netting agreement offset each other.
     */
    val netExposure: BigDecimal,
    val grossExposure: BigDecimal,
    val positionCount: Int,
    val nettingSetBreakdown: List<NettingSetExposure> = emptyList(),
)
