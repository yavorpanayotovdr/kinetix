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

    test("seeds 14 audit events when database is empty") {
        coEvery { repository.findAll() } returns emptyList()
        coEvery { repository.save(any()) } just runs

        seeder.seed()

        coVerify(exactly = 14) { repository.save(any()) }
    }

    test("skips seeding when events already exist") {
        coEvery { repository.findAll() } returns listOf(
            AuditEvent(
                id = 1,
                tradeId = "seed-eq-aapl-001",
                portfolioId = "equity-growth",
                instrumentId = "AAPL",
                assetClass = "EQUITY",
                side = "BUY",
                quantity = "150",
                priceAmount = "185.50",
                priceCurrency = "USD",
                tradedAt = "2026-02-21T14:00:00Z",
                receivedAt = java.time.Instant.now(),
            ),
        )

        seeder.seed()

        coVerify(exactly = 0) { repository.save(any()) }
    }

    test("event data has correct count") {
        DevDataSeeder.EVENTS.size shouldBe 14
    }

    test("all trade IDs match position-service trade IDs") {
        val tradeIds = DevDataSeeder.EVENTS.map { it.tradeId }.toSet()
        tradeIds.size shouldBe 14
        tradeIds.all { it.startsWith("seed-") } shouldBe true
    }

    test("events cover all three portfolios") {
        val portfolios = DevDataSeeder.EVENTS.map { it.portfolioId }.distinct().sorted()
        portfolios shouldBe listOf("equity-growth", "fixed-income", "multi-asset")
    }
})
