package com.kinetix.position.fix

import com.kinetix.common.model.Side
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.*
import java.math.BigDecimal
import java.time.Instant

class OrderSubmissionServiceTest : FunSpec({

    val orderRepository = mockk<ExecutionOrderRepository>()
    val sessionRepository = mockk<FIXSessionRepository>()
    val fixOrderSender = mockk<FIXOrderSender>()

    val service = OrderSubmissionService(orderRepository, sessionRepository, fixOrderSender)

    beforeEach {
        clearMocks(orderRepository, sessionRepository, fixOrderSender)
        coEvery { orderRepository.save(any()) } just runs
        coEvery { orderRepository.updateStatus(any(), any(), any(), any()) } just runs
        coEvery { fixOrderSender.send(any(), any()) } just runs
    }

    test("saves order and returns it with APPROVED status when no FIX session is provided") {
        val order = service.submit(
            bookId = "book-1",
            instrumentId = "AAPL",
            side = Side.BUY,
            quantity = BigDecimal("100"),
            orderType = "LIMIT",
            limitPrice = BigDecimal("150.00"),
            arrivalPrice = BigDecimal("149.90"),
            fixSessionId = null,
        )

        order.shouldNotBeNull()
        order.bookId shouldBe "book-1"
        order.instrumentId shouldBe "AAPL"
        order.side shouldBe Side.BUY
        order.quantity.compareTo(BigDecimal("100")) shouldBe 0
        order.status shouldBe OrderStatus.APPROVED

        coVerify(exactly = 1) { orderRepository.save(any()) }
        coVerify(exactly = 1) { orderRepository.updateStatus(any(), OrderStatus.APPROVED, "APPROVED", null) }
        coVerify(exactly = 0) { fixOrderSender.send(any(), any()) }
    }

    test("dispatches order via FIX and returns SENT status when session is connected") {
        val session = FIXSession(
            sessionId = "FIX-01",
            counterparty = "BROKER",
            status = FIXSessionStatus.CONNECTED,
            lastMessageAt = Instant.now(),
            inboundSeqNum = 10,
            outboundSeqNum = 8,
        )
        coEvery { sessionRepository.findById("FIX-01") } returns session

        val order = service.submit(
            bookId = "book-1",
            instrumentId = "AAPL",
            side = Side.BUY,
            quantity = BigDecimal("200"),
            orderType = "MARKET",
            limitPrice = null,
            arrivalPrice = BigDecimal("149.80"),
            fixSessionId = "FIX-01",
        )

        order.status shouldBe OrderStatus.SENT
        coVerify(exactly = 1) { fixOrderSender.send(match { it.status == OrderStatus.APPROVED }, session) }
        coVerify(exactly = 1) { orderRepository.updateStatus(any(), OrderStatus.SENT) }
    }

    test("remains APPROVED without FIX dispatch when session is disconnected") {
        val session = FIXSession(
            sessionId = "FIX-02",
            counterparty = "BROKER",
            status = FIXSessionStatus.DISCONNECTED,
            lastMessageAt = null,
            inboundSeqNum = 0,
            outboundSeqNum = 0,
        )
        coEvery { sessionRepository.findById("FIX-02") } returns session

        val order = service.submit(
            bookId = "book-1",
            instrumentId = "AAPL",
            side = Side.SELL,
            quantity = BigDecimal("50"),
            orderType = "LIMIT",
            limitPrice = BigDecimal("160.00"),
            arrivalPrice = BigDecimal("159.90"),
            fixSessionId = "FIX-02",
        )

        order.status shouldBe OrderStatus.APPROVED
        coVerify(exactly = 0) { fixOrderSender.send(any(), any()) }
    }

    test("remains APPROVED without FIX dispatch when session is not found") {
        coEvery { sessionRepository.findById("MISSING") } returns null

        val order = service.submit(
            bookId = "book-1",
            instrumentId = "AAPL",
            side = Side.BUY,
            quantity = BigDecimal("10"),
            orderType = "MARKET",
            limitPrice = null,
            arrivalPrice = BigDecimal("150.00"),
            fixSessionId = "MISSING",
        )

        order.status shouldBe OrderStatus.APPROVED
        coVerify(exactly = 0) { fixOrderSender.send(any(), any()) }
    }

    test("rejects zero quantity with IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            service.submit(
                bookId = "book-1",
                instrumentId = "AAPL",
                side = Side.BUY,
                quantity = BigDecimal.ZERO,
                orderType = "MARKET",
                limitPrice = null,
                arrivalPrice = BigDecimal("150.00"),
                fixSessionId = null,
            )
        }

        coVerify(exactly = 0) { orderRepository.save(any()) }
    }
})
