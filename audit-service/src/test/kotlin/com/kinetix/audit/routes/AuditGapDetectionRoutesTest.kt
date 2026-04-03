package com.kinetix.audit.routes

import com.kinetix.audit.model.AuditEvent
import com.kinetix.audit.module
import com.kinetix.audit.persistence.AuditEventRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*
import java.time.Instant

class AuditGapDetectionRoutesTest : FunSpec({

    val repository = mockk<AuditEventRepository>()

    beforeEach { clearMocks(repository) }

    test("GET /api/v1/audit/gaps returns empty list when no gaps exist in a contiguous sequence") {
        // Sequence numbers 1, 2, 3 — contiguous, no gaps
        val events = listOf(
            auditEvent(id = 1, sequenceNumber = 1L),
            auditEvent(id = 2, sequenceNumber = 2L),
            auditEvent(id = 3, sequenceNumber = 3L),
        )
        coEvery { repository.findAll() } returns events

        testApplication {
            application { module(repository) }
            val response = client.get("/api/v1/audit/gaps")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["gapCount"]?.jsonPrimitive?.int shouldBe 0
            body["gaps"]?.jsonArray?.size shouldBe 0
        }
    }

    test("GET /api/v1/audit/gaps returns gaps when sequence numbers are non-contiguous") {
        // Sequence numbers 1, 2, 5 — gap between 2 and 5 means 3 and 4 are missing
        val events = listOf(
            auditEvent(id = 1, sequenceNumber = 1L),
            auditEvent(id = 2, sequenceNumber = 2L),
            auditEvent(id = 5, sequenceNumber = 5L),
        )
        coEvery { repository.findAll() } returns events

        testApplication {
            application { module(repository) }
            val response = client.get("/api/v1/audit/gaps")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["gapCount"]?.jsonPrimitive?.int shouldBe 1
            val gaps = body["gaps"]?.jsonArray!!
            gaps.size shouldBe 1
            val gap = gaps[0].jsonObject
            gap["afterSequence"]?.jsonPrimitive?.long shouldBe 2L
            gap["beforeSequence"]?.jsonPrimitive?.long shouldBe 5L
            gap["missingCount"]?.jsonPrimitive?.long shouldBe 2L
        }
    }

    test("GET /api/v1/audit/gaps returns multiple gaps when several ranges are missing") {
        // Sequence 1, 3, 7 — gaps: [1..3) and [3..7)
        val events = listOf(
            auditEvent(id = 1, sequenceNumber = 1L),
            auditEvent(id = 3, sequenceNumber = 3L),
            auditEvent(id = 7, sequenceNumber = 7L),
        )
        coEvery { repository.findAll() } returns events

        testApplication {
            application { module(repository) }
            val response = client.get("/api/v1/audit/gaps")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["gapCount"]?.jsonPrimitive?.int shouldBe 2
        }
    }

    test("GET /api/v1/audit/gaps returns empty result when no events exist") {
        coEvery { repository.findAll() } returns emptyList()

        testApplication {
            application { module(repository) }
            val response = client.get("/api/v1/audit/gaps")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["gapCount"]?.jsonPrimitive?.int shouldBe 0
        }
    }

    test("GET /api/v1/audit/gaps ignores events with null sequence numbers") {
        // Events without sequence numbers (legacy entries before migration) are excluded from gap detection
        val events = listOf(
            auditEvent(id = 1, sequenceNumber = null),
            auditEvent(id = 2, sequenceNumber = 1L),
            auditEvent(id = 3, sequenceNumber = 2L),
        )
        coEvery { repository.findAll() } returns events

        testApplication {
            application { module(repository) }
            val response = client.get("/api/v1/audit/gaps")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["gapCount"]?.jsonPrimitive?.int shouldBe 0
        }
    }
})

private fun auditEvent(id: Long, sequenceNumber: Long?) = AuditEvent(
    id = id,
    tradeId = "t-$id",
    bookId = "book-1",
    eventType = "TRADE_BOOKED",
    receivedAt = Instant.parse("2026-01-01T10:00:00Z"),
    sequenceNumber = sequenceNumber,
)
