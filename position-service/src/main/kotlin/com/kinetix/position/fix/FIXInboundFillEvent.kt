package com.kinetix.position.fix

import java.math.BigDecimal

/**
 * Parsed representation of a FIX ExecutionReport (MsgType=8).
 * Produced by [FIXMessageConverter] from raw FIX tag=value strings.
 */
data class FIXInboundFillEvent(
    val sessionId: String,
    val execId: String,
    val orderId: String,
    val execType: String,
    val lastQty: BigDecimal,
    val lastPrice: BigDecimal,
    val cumulativeQty: BigDecimal,
    val averagePrice: BigDecimal,
    val venue: String?,
)
