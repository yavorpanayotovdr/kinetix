package com.kinetix.audit.persistence

import com.kinetix.audit.model.AuditEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import java.time.Instant

private val NOW = Instant.parse("2026-01-15T10:00:00Z")

private fun governanceEvent(
    id: Long = 0,
    eventType: String = "MODEL_STATUS_CHANGED",
    userId: String = "user-1",
    userRole: String = "RISK_MANAGER",
    modelName: String? = "VaR-v2",
    scenarioId: String? = null,
    limitId: String? = null,
    submissionId: String? = null,
    bookId: String? = "BOOK-A",
    details: String? = "DRAFT->VALIDATED",
    receivedAt: Instant = NOW,
) = AuditEvent(
    id = id,
    tradeId = null,
    bookId = bookId,
    instrumentId = null,
    assetClass = null,
    side = null,
    quantity = null,
    priceAmount = null,
    priceCurrency = null,
    tradedAt = null,
    receivedAt = receivedAt,
    userId = userId,
    userRole = userRole,
    eventType = eventType,
    modelName = modelName,
    scenarioId = scenarioId,
    limitId = limitId,
    submissionId = submissionId,
    details = details,
)

class GovernanceAuditHashTest : FunSpec({

    test("governance event produces a deterministic 64-character SHA-256 hash") {
        val event = governanceEvent()
        val hash1 = AuditHasher.computeHash(event, previousHash = null)
        val hash2 = AuditHasher.computeHash(event, previousHash = null)

        hash1 shouldHaveLength 64
        hash1 shouldBe hash2
    }

    test("governance event hash changes when details field changes") {
        val event = governanceEvent(details = "DRAFT->VALIDATED")
        val hash1 = AuditHasher.computeHash(event, previousHash = null)

        val eventModified = governanceEvent(details = "VALIDATED->APPROVED")
        val hash2 = AuditHasher.computeHash(eventModified, previousHash = null)

        (hash1 != hash2) shouldBe true
    }

    test("governance event hash changes when modelName field changes") {
        val event = governanceEvent(modelName = "VaR-v2")
        val hash1 = AuditHasher.computeHash(event, previousHash = null)

        val eventModified = governanceEvent(modelName = "VaR-v3")
        val hash2 = AuditHasher.computeHash(eventModified, previousHash = null)

        (hash1 != hash2) shouldBe true
    }

    test("chain with mixed trade and governance events verifies correctly") {
        val tradeEvent = AuditEvent(
            id = 1,
            tradeId = "t-1",
            bookId = "port-1",
            instrumentId = "AAPL",
            assetClass = "EQUITY",
            side = "BUY",
            quantity = "100",
            priceAmount = "150",
            priceCurrency = "USD",
            tradedAt = "2026-01-15T10:00:00Z",
            receivedAt = NOW,
            userId = "trader-1",
            userRole = "TRADER",
            eventType = "TRADE_BOOKED",
        )
        val hash1 = AuditHasher.computeHash(tradeEvent, previousHash = null)
        val chained1 = tradeEvent.copy(previousHash = null, recordHash = hash1)

        val govEvent = governanceEvent(id = 2, receivedAt = NOW.plusSeconds(1))
        val hash2 = AuditHasher.computeHash(govEvent, previousHash = hash1)
        val chained2 = govEvent.copy(previousHash = hash1, recordHash = hash2)

        val result = AuditHasher.verifyChain(listOf(chained1, chained2))
        result.valid shouldBe true
        result.eventCount shouldBe 2
    }

    test("chain with governance events fails when scenarioId is tampered") {
        val event1 = governanceEvent(id = 1, scenarioId = "sc-1")
        val hash1 = AuditHasher.computeHash(event1, previousHash = null)
        val chained1 = event1.copy(previousHash = null, recordHash = hash1)

        val event2 = governanceEvent(id = 2, scenarioId = "sc-2", receivedAt = NOW.plusSeconds(1))
        val hash2 = AuditHasher.computeHash(event2, previousHash = hash1)
        val tampered2 = event2.copy(scenarioId = "sc-tampered", previousHash = hash1, recordHash = hash2)

        val result = AuditHasher.verifyChain(listOf(chained1, tampered2))
        result.valid shouldBe false
    }

    test("null trade fields in governance event do not cause hash to match non-null equivalent") {
        val govEvent = governanceEvent(id = 1)
        val hashGov = AuditHasher.computeHash(govEvent, previousHash = null)

        val tradeEvent = AuditEvent(
            id = 1,
            tradeId = "t-1",
            bookId = "BOOK-A",
            instrumentId = "AAPL",
            assetClass = "EQUITY",
            side = "BUY",
            quantity = "100",
            priceAmount = "150",
            priceCurrency = "USD",
            tradedAt = "2026-01-15T10:00:00Z",
            receivedAt = NOW,
            userId = "user-1",
            userRole = "RISK_MANAGER",
            eventType = "MODEL_STATUS_CHANGED",
        )
        val hashTrade = AuditHasher.computeHash(tradeEvent, previousHash = null)

        (hashGov != hashTrade) shouldBe true
    }
})
