package com.kinetix.risk.reconciliation

import com.kinetix.risk.client.AuditServiceClient
import com.kinetix.risk.client.ClientResponse
import com.kinetix.risk.client.PositionServiceInternalClient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class TradeAuditReconciliationJobTest : FunSpec({

    val fixedInstant = Instant.parse("2026-03-19T10:00:00Z")
    val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    test("detects mismatch between trade and audit counts") {
        val positionClient = mockk<PositionServiceInternalClient>()
        val auditClient = mockk<AuditServiceClient>()

        coEvery { positionClient.countTradeEventsSince(any()) } returns ClientResponse.Success(100L)
        coEvery { auditClient.countAuditEventsSince(any()) } returns ClientResponse.Success(95L)

        val job = TradeAuditReconciliationJob(
            positionServiceClient = positionClient,
            auditServiceClient = auditClient,
            clock = fixedClock,
        )

        val result = job.runReconciliation()

        result.shouldNotBeNull()
        result.tradeCount shouldBe 100L
        result.auditCount shouldBe 95L
        result.matched shouldBe false
    }

    test("reports clean reconciliation when counts match") {
        val positionClient = mockk<PositionServiceInternalClient>()
        val auditClient = mockk<AuditServiceClient>()

        coEvery { positionClient.countTradeEventsSince(any()) } returns ClientResponse.Success(42L)
        coEvery { auditClient.countAuditEventsSince(any()) } returns ClientResponse.Success(42L)

        val job = TradeAuditReconciliationJob(
            positionServiceClient = positionClient,
            auditServiceClient = auditClient,
            clock = fixedClock,
        )

        val result = job.runReconciliation()

        result.shouldNotBeNull()
        result.tradeCount shouldBe 42L
        result.auditCount shouldBe 42L
        result.matched shouldBe true
    }

    test("handles position-service unavailability gracefully") {
        val positionClient = mockk<PositionServiceInternalClient>()
        val auditClient = mockk<AuditServiceClient>()

        coEvery { positionClient.countTradeEventsSince(any()) } returns ClientResponse.NotFound(404)

        val job = TradeAuditReconciliationJob(
            positionServiceClient = positionClient,
            auditServiceClient = auditClient,
            clock = fixedClock,
        )

        val result = job.runReconciliation()

        result.shouldBeNull()
    }

    test("handles audit-service unavailability gracefully") {
        val positionClient = mockk<PositionServiceInternalClient>()
        val auditClient = mockk<AuditServiceClient>()

        coEvery { positionClient.countTradeEventsSince(any()) } returns ClientResponse.Success(10L)
        coEvery { auditClient.countAuditEventsSince(any()) } returns ClientResponse.NotFound(404)

        val job = TradeAuditReconciliationJob(
            positionServiceClient = positionClient,
            auditServiceClient = auditClient,
            clock = fixedClock,
        )

        val result = job.runReconciliation()

        result.shouldBeNull()
    }

    test("uses the configured window duration for the since timestamp") {
        val positionClient = mockk<PositionServiceInternalClient>()
        val auditClient = mockk<AuditServiceClient>()

        var capturedSince: Instant? = null
        coEvery { positionClient.countTradeEventsSince(any()) } answers {
            capturedSince = firstArg()
            ClientResponse.Success(0L)
        }
        coEvery { auditClient.countAuditEventsSince(any()) } returns ClientResponse.Success(0L)

        val job = TradeAuditReconciliationJob(
            positionServiceClient = positionClient,
            auditServiceClient = auditClient,
            clock = fixedClock,
        )

        job.runReconciliation()

        capturedSince.shouldNotBeNull()
        capturedSince shouldBe Instant.parse("2026-03-18T10:00:00Z")
    }
})
