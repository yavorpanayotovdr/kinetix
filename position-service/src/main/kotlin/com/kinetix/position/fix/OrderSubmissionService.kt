package com.kinetix.position.fix

import com.kinetix.common.model.Side
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Handles the lifecycle of a new order from client submission through optional FIX dispatch.
 *
 * The flow is:
 *   1. Validate and persist the order with status PENDING_RISK_CHECK.
 *   2. Auto-approve (pre-trade risk check integration point for future work).
 *   3. If a connected FIX session is supplied, dispatch via [FIXOrderSender] and advance status to SENT.
 *   4. Return the order in its final status.
 *
 * This class is intentionally free of infrastructure concerns — all I/O is injected so that
 * unit tests can run without a database or FIX session.
 */
class OrderSubmissionService(
    private val orderRepository: ExecutionOrderRepository,
    private val sessionRepository: FIXSessionRepository,
    private val fixOrderSender: FIXOrderSender,
) {

    private val logger = LoggerFactory.getLogger(OrderSubmissionService::class.java)

    suspend fun submit(
        bookId: String,
        instrumentId: String,
        side: Side,
        quantity: BigDecimal,
        orderType: String,
        limitPrice: BigDecimal?,
        arrivalPrice: BigDecimal,
        fixSessionId: String?,
    ): Order {
        require(quantity > BigDecimal.ZERO) { "Quantity must be positive" }

        val order = Order(
            orderId = UUID.randomUUID().toString(),
            bookId = bookId,
            instrumentId = instrumentId,
            side = side,
            quantity = quantity,
            orderType = orderType,
            limitPrice = limitPrice,
            arrivalPrice = arrivalPrice,
            submittedAt = Instant.now(),
            status = OrderStatus.PENDING_RISK_CHECK,
            riskCheckResult = null,
            riskCheckDetails = null,
            fixSessionId = fixSessionId,
        )
        orderRepository.save(order)
        logger.info("Order created: orderId={}, book={}, instrument={}, side={}, qty={}",
            order.orderId, bookId, instrumentId, side, quantity)

        // Pre-trade risk check integration point — auto-approve for now.
        orderRepository.updateStatus(order.orderId, OrderStatus.APPROVED, "APPROVED", null)
        val approved = order.copy(status = OrderStatus.APPROVED, riskCheckResult = "APPROVED")

        if (fixSessionId == null) {
            return approved
        }

        val session = sessionRepository.findById(fixSessionId)
        if (session == null || session.status != FIXSessionStatus.CONNECTED) {
            logger.warn("FIX session {} not connected — order {} remains APPROVED without dispatch",
                fixSessionId, approved.orderId)
            return approved
        }

        fixOrderSender.send(approved, session)
        orderRepository.updateStatus(approved.orderId, OrderStatus.SENT)
        logger.info("Order dispatched via FIX: orderId={}, session={}", approved.orderId, fixSessionId)

        return approved.copy(status = OrderStatus.SENT)
    }
}
