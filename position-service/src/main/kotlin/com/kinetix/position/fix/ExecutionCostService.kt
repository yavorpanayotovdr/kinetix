package com.kinetix.position.fix

import com.kinetix.common.model.Side
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Computes execution cost analysis for a fully-filled order.
 *
 * slippage_bps = (avg_fill_price - arrival_price) / arrival_price * 10000 * side_sign
 *
 * side_sign:
 *   BUY  = +1: paying above arrival is a cost (positive slippage)
 *   SELL = -1: receiving below arrival is a cost (positive slippage via negation)
 *
 * Positive slippage = paid more than expected (cost).
 * Negative slippage = paid less than expected (savings).
 */
class ExecutionCostService {

    private val logger = LoggerFactory.getLogger(ExecutionCostService::class.java)

    fun compute(order: Order, completedAt: Instant): ExecutionCostAnalysis {
        val avgFillPrice = order.averageFillPrice
            ?: throw IllegalArgumentException("Cannot compute execution cost for order with no fills: ${order.orderId}")

        val sideSign = when (order.side) {
            Side.BUY -> BigDecimal.ONE
            Side.SELL -> BigDecimal("-1")
        }

        val slippageBps = (avgFillPrice - order.arrivalPrice)
            .divide(order.arrivalPrice, 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal("10000"))
            .multiply(sideSign)

        logger.debug(
            "Execution cost: orderId={}, arrivalPrice={}, avgFillPrice={}, slippageBps={}",
            order.orderId, order.arrivalPrice, avgFillPrice, slippageBps,
        )

        return ExecutionCostAnalysis(
            orderId = order.orderId,
            bookId = order.bookId,
            instrumentId = order.instrumentId,
            completedAt = completedAt,
            arrivalPrice = order.arrivalPrice,
            averageFillPrice = avgFillPrice,
            side = order.side,
            totalQty = order.filledQuantity,
            metrics = ExecutionCostMetrics(
                slippageBps = slippageBps,
                marketImpactBps = null,
                timingCostBps = null,
                totalCostBps = slippageBps,
            ),
        )
    }
}
