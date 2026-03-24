package com.kinetix.position.fix

import java.math.BigDecimal
import java.time.Instant

data class ExecutionFill(
    val fillId: String,
    val orderId: String,
    val bookId: String,
    val instrumentId: String,
    val fillTime: Instant,
    val fillQty: BigDecimal,
    val fillPrice: BigDecimal,
    val fillType: FillType,
    val venue: String?,
    val cumulativeQty: BigDecimal,
    val averagePrice: BigDecimal,
    val fixExecId: String?,
)
