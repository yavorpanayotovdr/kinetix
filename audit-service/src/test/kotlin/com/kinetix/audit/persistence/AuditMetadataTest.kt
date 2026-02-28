package com.kinetix.audit.persistence

import com.kinetix.audit.model.AuditEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import java.time.Instant

private val NOW = Instant.parse("2026-01-15T10:00:00Z")

class AuditMetadataTest : FunSpec({

    test("event should store userId and userRole and eventType") {
        val event = AuditEvent(
            id = 1,
            tradeId = "t-1",
            portfolioId = "port-1",
            instrumentId = "AAPL",
            assetClass = "EQUITY",
            side = "BUY",
            quantity = "100",
            priceAmount = "150.00",
            priceCurrency = "USD",
            tradedAt = "2026-01-15T10:00:00Z",
            receivedAt = NOW,
            userId = "user-123",
            userRole = "TRADER",
            eventType = "TRADE_BOOKED",
        )

        event.userId shouldBe "user-123"
        event.userRole shouldBe "TRADER"
        event.eventType shouldBe "TRADE_BOOKED"
    }

    test("legacy events should have null userId") {
        val event = AuditEvent(
            id = 1,
            tradeId = "t-1",
            portfolioId = "port-1",
            instrumentId = "AAPL",
            assetClass = "EQUITY",
            side = "BUY",
            quantity = "100",
            priceAmount = "150.00",
            priceCurrency = "USD",
            tradedAt = "2026-01-15T10:00:00Z",
            receivedAt = NOW,
        )

        event.userId.shouldBeNull()
        event.userRole.shouldBeNull()
        event.eventType shouldBe "TRADE_BOOKED"
    }
})
