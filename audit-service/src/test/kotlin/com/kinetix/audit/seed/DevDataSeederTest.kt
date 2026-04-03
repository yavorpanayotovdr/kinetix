package com.kinetix.audit.seed

import com.kinetix.audit.model.AuditEvent
import com.kinetix.audit.persistence.AuditEventRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*

class DevDataSeederTest : FunSpec({

    val repository = mockk<AuditEventRepository>()
    val seeder = DevDataSeeder(repository)

    beforeEach {
        clearMocks(repository)
    }

    test("seeds audit events when anchor trade is absent") {
        coEvery { repository.findByTradeId("seed-eq-aapl-001") } returns null
        coEvery { repository.save(any()) } just runs

        seeder.seed()

        coVerify(exactly = DevDataSeeder.EVENTS.size) { repository.save(any()) }
    }

    test("skips seeding when anchor trade already exists") {
        coEvery { repository.findByTradeId("seed-eq-aapl-001") } returns AuditEvent(
            id = 1,
            tradeId = "seed-eq-aapl-001",
            bookId = "equity-growth",
            instrumentId = "AAPL",
            assetClass = "EQUITY",
            side = "BUY",
            quantity = "25000",
            priceAmount = "185.50",
            priceCurrency = "USD",
            tradedAt = "2026-02-21T14:00:00Z",
            receivedAt = java.time.Instant.now(),
        )

        seeder.seed()

        coVerify(exactly = 0) { repository.save(any()) }
    }

    test("event data has correct count") {
        DevDataSeeder.EVENTS.size shouldBe 44
    }

    test("all seed events have non-null userId and userRole") {
        DevDataSeeder.EVENTS.all { it.userId != null && it.userRole != null } shouldBe true
    }

    test("seed events include at least two distinct userIds") {
        val userIds = DevDataSeeder.EVENTS.mapNotNull { it.userId }.distinct()
        (userIds.size >= 2) shouldBe true
    }

    test("all trade IDs are unique and match seed convention") {
        val tradeIds = DevDataSeeder.EVENTS.map { it.tradeId }.toSet()
        tradeIds.size shouldBe DevDataSeeder.EVENTS.size
        tradeIds.all { it?.startsWith("seed-") == true } shouldBe true
    }

    test("events cover all eight books") {
        val portfolios = DevDataSeeder.EVENTS.mapNotNull { it.bookId }.distinct().sorted()
        portfolios shouldBe listOf(
            "balanced-income", "derivatives-book", "emerging-markets",
            "equity-growth", "fixed-income", "macro-hedge",
            "multi-asset", "tech-momentum",
        )
    }
})
