package com.kinetix.gateway.websocket

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PricePoint
import com.kinetix.common.model.PriceSource
import com.kinetix.common.model.Money
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.mockk.*
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")

private fun aaplPrice(amount: String = "170.00") = PricePoint(
    instrumentId = InstrumentId("AAPL"),
    price = Money(BigDecimal(amount), USD),
    timestamp = Instant.parse("2025-06-15T14:30:00Z"),
    source = PriceSource.BLOOMBERG,
)

private fun msftPrice(amount: String = "400.00") = PricePoint(
    instrumentId = InstrumentId("MSFT"),
    price = Money(BigDecimal(amount), USD),
    timestamp = Instant.parse("2025-06-15T14:30:00Z"),
    source = PriceSource.REUTERS,
)

private fun mockSession(): WebSocketServerSession {
    val session = mockk<WebSocketServerSession>(relaxed = true)
    coEvery { session.send(any<Frame>()) } just Runs
    return session
}

class PriceBroadcasterTest : FunSpec({

    test("broadcast sends to subscribed session") {
        val broadcaster = PriceBroadcaster()
        val session = mockSession()
        broadcaster.subscribe(session, listOf("AAPL"))

        broadcaster.broadcast(aaplPrice())

        coVerify(exactly = 1) { session.send(any<Frame>()) }
    }

    test("broadcast does not send to session subscribed to different instrument") {
        val broadcaster = PriceBroadcaster()
        val session = mockSession()
        broadcaster.subscribe(session, listOf("MSFT"))

        broadcaster.broadcast(aaplPrice())

        coVerify(exactly = 0) { session.send(any<Frame>()) }
    }

    test("broadcast sends to multiple subscribed sessions") {
        val broadcaster = PriceBroadcaster()
        val session1 = mockSession()
        val session2 = mockSession()
        broadcaster.subscribe(session1, listOf("AAPL"))
        broadcaster.subscribe(session2, listOf("AAPL"))

        broadcaster.broadcast(aaplPrice())

        coVerify(exactly = 1) { session1.send(any<Frame>()) }
        coVerify(exactly = 1) { session2.send(any<Frame>()) }
    }

    test("subscribe to multiple instruments receives both") {
        val broadcaster = PriceBroadcaster()
        val session = mockSession()
        broadcaster.subscribe(session, listOf("AAPL", "MSFT"))

        broadcaster.broadcast(aaplPrice())
        broadcaster.broadcast(msftPrice())

        coVerify(exactly = 2) { session.send(any<Frame>()) }
    }

    test("unsubscribe stops delivery") {
        val broadcaster = PriceBroadcaster()
        val session = mockSession()
        broadcaster.subscribe(session, listOf("AAPL"))
        broadcaster.unsubscribe(session, listOf("AAPL"))

        broadcaster.broadcast(aaplPrice())

        coVerify(exactly = 0) { session.send(any<Frame>()) }
    }

    test("removeSession cleans up all subscriptions") {
        val broadcaster = PriceBroadcaster()
        val session = mockSession()
        broadcaster.subscribe(session, listOf("AAPL", "MSFT"))
        broadcaster.removeSession(session)

        broadcaster.broadcast(aaplPrice())
        broadcaster.broadcast(msftPrice())

        coVerify(exactly = 0) { session.send(any<Frame>()) }
    }

    test("broadcast with no subscriptions is a no-op") {
        val broadcaster = PriceBroadcaster()
        // should not throw
        broadcaster.broadcast(aaplPrice())
    }

    test("dead session is auto-removed on broadcast") {
        val broadcaster = PriceBroadcaster()
        val deadSession = mockSession()
        coEvery { deadSession.send(any<Frame>()) } throws RuntimeException("Connection closed")
        broadcaster.subscribe(deadSession, listOf("AAPL"))

        broadcaster.broadcast(aaplPrice())

        // second broadcast should not attempt send again
        broadcaster.broadcast(aaplPrice())
        coVerify(exactly = 1) { deadSession.send(any<Frame>()) }
    }
})
