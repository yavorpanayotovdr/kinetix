package com.kinetix.audit.persistence

import com.kinetix.audit.model.AuditEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant

private val NOW = Instant.parse("2026-01-15T10:00:00Z")

private fun auditEvent(
    tradeId: String = "t-1",
    portfolioId: String = "port-1",
    instrumentId: String = "AAPL",
    assetClass: String = "EQUITY",
    side: String = "BUY",
    quantity: String = "100",
    priceAmount: String = "150.00",
    priceCurrency: String = "USD",
    tradedAt: String = "2026-01-15T10:00:00Z",
    receivedAt: Instant = NOW,
) = AuditEvent(
    tradeId = tradeId,
    portfolioId = portfolioId,
    instrumentId = instrumentId,
    assetClass = assetClass,
    side = side,
    quantity = quantity,
    priceAmount = priceAmount,
    priceCurrency = priceCurrency,
    tradedAt = tradedAt,
    receivedAt = receivedAt,
)

class ExposedAuditEventRepositoryIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository: AuditEventRepository = ExposedAuditEventRepository()

    beforeEach {
        newSuspendedTransaction { AuditEventsTable.deleteAll() }
    }

    test("save and findAll returns the event") {
        repository.save(auditEvent())

        val found = repository.findAll()
        found shouldHaveSize 1
        found[0].tradeId shouldBe "t-1"
        found[0].portfolioId shouldBe "port-1"
        found[0].instrumentId shouldBe "AAPL"
        found[0].side shouldBe "BUY"
        found[0].quantity shouldBe "100"
        found[0].priceAmount shouldBe "150.00"
        found[0].priceCurrency shouldBe "USD"
    }

    test("findByPortfolioId returns only matching events") {
        repository.save(auditEvent(tradeId = "t-1", portfolioId = "port-1"))
        repository.save(auditEvent(tradeId = "t-2", portfolioId = "port-2"))
        repository.save(auditEvent(tradeId = "t-3", portfolioId = "port-1"))

        val results = repository.findByPortfolioId("port-1")
        results shouldHaveSize 2
        results.forEach { it.portfolioId shouldBe "port-1" }
    }

    test("findByPortfolioId returns empty list for unknown portfolio") {
        repository.findByPortfolioId("unknown") shouldHaveSize 0
    }

    test("findAll returns events in insertion order") {
        repository.save(auditEvent(tradeId = "t-1"))
        repository.save(auditEvent(tradeId = "t-2"))
        repository.save(auditEvent(tradeId = "t-3"))

        val found = repository.findAll()
        found shouldHaveSize 3
        found[0].tradeId shouldBe "t-1"
        found[1].tradeId shouldBe "t-2"
        found[2].tradeId shouldBe "t-3"
    }

    test("save preserves all fields accurately") {
        val event = auditEvent(
            tradeId = "t-precise",
            portfolioId = "port-precise",
            instrumentId = "TSLA",
            assetClass = "EQUITY",
            side = "SELL",
            quantity = "500.5",
            priceAmount = "199.99",
            priceCurrency = "EUR",
            tradedAt = "2026-06-15T14:30:00Z",
        )
        repository.save(event)

        val found = repository.findAll()
        found shouldHaveSize 1
        found[0].tradeId shouldBe "t-precise"
        found[0].instrumentId shouldBe "TSLA"
        found[0].side shouldBe "SELL"
        found[0].quantity shouldBe "500.5"
        found[0].priceAmount shouldBe "199.99"
        found[0].priceCurrency shouldBe "EUR"
    }
})
