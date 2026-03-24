package com.kinetix.position.fix

import com.kinetix.common.model.Side
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

data class Order(
    val orderId: String,
    val bookId: String,
    val instrumentId: String,
    val side: Side,
    val quantity: BigDecimal,
    val orderType: String,
    val limitPrice: BigDecimal?,
    val arrivalPrice: BigDecimal,
    val submittedAt: Instant,
    val status: OrderStatus,
    val riskCheckResult: String?,
    val riskCheckDetails: String?,
    val fixSessionId: String?,
    val fills: List<ExecutionFill> = emptyList(),
) {
    val isTerminal: Boolean
        get() = status.isTerminal

    val filledQuantity: BigDecimal
        get() = fills.fold(BigDecimal.ZERO) { acc, fill -> acc + fill.fillQty }

    val averageFillPrice: BigDecimal?
        get() {
            if (fills.isEmpty()) return null
            val totalValue = fills.fold(BigDecimal.ZERO) { acc, fill ->
                acc + (fill.fillPrice * fill.fillQty)
            }
            return if (filledQuantity.signum() == 0) null
            else totalValue.divide(filledQuantity, 10, RoundingMode.HALF_UP)
        }
}
