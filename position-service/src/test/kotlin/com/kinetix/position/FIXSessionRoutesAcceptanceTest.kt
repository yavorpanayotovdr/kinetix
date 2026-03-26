package com.kinetix.position

import com.kinetix.position.fix.FIXSession
import com.kinetix.position.fix.FIXSessionEventPublisher
import com.kinetix.position.fix.FIXSessionRepository
import com.kinetix.position.fix.FIXSessionStatus
import com.kinetix.position.routes.fixSessionRoutes
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.request.patch
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

private fun Application.configureFixSessionTestApp(
    fixSessionRepository: FIXSessionRepository,
    sessionEventPublisher: FIXSessionEventPublisher? = null,
) {
    install(ContentNegotiation) { json() }
    routing {
        fixSessionRoutes(fixSessionRepository, sessionEventPublisher)
    }
}

class FIXSessionRoutesAcceptanceTest : FunSpec({

    val fixSessionRepository = mockk<FIXSessionRepository>()

    test("GET /api/v1/fix/sessions returns empty list when no sessions exist") {
        coEvery { fixSessionRepository.findAll() } returns emptyList()

        testApplication {
            application { configureFixSessionTestApp(fixSessionRepository) }
            val response = client.get("/api/v1/fix/sessions")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 0
        }
    }

    test("GET /api/v1/fix/sessions returns connected sessions") {
        val connectedAt = Instant.parse("2026-03-24T10:00:00Z")
        coEvery { fixSessionRepository.findAll() } returns listOf(
            FIXSession(
                sessionId = "FIX-BROKER-01",
                counterparty = "Goldman Sachs",
                status = FIXSessionStatus.CONNECTED,
                lastMessageAt = connectedAt,
                inboundSeqNum = 1042,
                outboundSeqNum = 988,
            )
        )

        testApplication {
            application { configureFixSessionTestApp(fixSessionRepository) }
            val response = client.get("/api/v1/fix/sessions")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 1
            body[0].jsonObject["sessionId"]!!.jsonPrimitive.content shouldBe "FIX-BROKER-01"
            body[0].jsonObject["status"]!!.jsonPrimitive.content shouldBe "CONNECTED"
            body[0].jsonObject["counterparty"]!!.jsonPrimitive.content shouldBe "Goldman Sachs"
            body[0].jsonObject["inboundSeqNum"]!!.jsonPrimitive.content shouldBe "1042"
        }
    }

    test("GET /api/v1/fix/sessions returns disconnected sessions with null lastMessageAt") {
        coEvery { fixSessionRepository.findAll() } returns listOf(
            FIXSession(
                sessionId = "FIX-BROKER-02",
                counterparty = "JPMorgan",
                status = FIXSessionStatus.DISCONNECTED,
                lastMessageAt = null,
                inboundSeqNum = 0,
                outboundSeqNum = 0,
            )
        )

        testApplication {
            application { configureFixSessionTestApp(fixSessionRepository) }
            val response = client.get("/api/v1/fix/sessions")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body[0].jsonObject["status"]!!.jsonPrimitive.content shouldBe "DISCONNECTED"
            body[0].jsonObject.containsKey("lastMessageAt") shouldBe true
        }
    }

    test("GET /api/v1/fix/sessions returns multiple sessions with mixed status") {
        coEvery { fixSessionRepository.findAll() } returns listOf(
            FIXSession("S1", "Broker A", FIXSessionStatus.CONNECTED, Instant.now(), 100, 50),
            FIXSession("S2", "Broker B", FIXSessionStatus.DISCONNECTED, null, 0, 0),
            FIXSession("S3", "Broker C", FIXSessionStatus.RECONNECTING, Instant.now(), 25, 12),
        )

        testApplication {
            application { configureFixSessionTestApp(fixSessionRepository) }
            val response = client.get("/api/v1/fix/sessions")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 3
            response.bodyAsText() shouldContain "RECONNECTING"
        }
    }

    // EXEC-06: FIX session disconnect events
    test("PATCH /api/v1/fix/sessions/{id}/status to DISCONNECTED emits FIX_SESSION_DISCONNECTED event") {
        val publisher = mockk<FIXSessionEventPublisher>(relaxed = true)
        val session = FIXSession("FIX-01", "Goldman Sachs", FIXSessionStatus.CONNECTED, Instant.now(), 100, 50)
        coEvery { fixSessionRepository.findById("FIX-01") } returns session
        coEvery { fixSessionRepository.updateStatus("FIX-01", FIXSessionStatus.DISCONNECTED) } returns Unit

        testApplication {
            application { configureFixSessionTestApp(fixSessionRepository, publisher) }
            val response = client.patch("/api/v1/fix/sessions/FIX-01/status") {
                contentType(ContentType.Application.Json)
                setBody("""{"status": "DISCONNECTED"}""")
            }
            response.status shouldBe HttpStatusCode.NoContent
            coVerify(exactly = 1) {
                publisher.publishDisconnected(match {
                    it.sessionId == "FIX-01" && it.counterparty == "Goldman Sachs"
                })
            }
        }
    }

    test("PATCH session status to CONNECTED does not emit disconnect event") {
        val publisher = mockk<FIXSessionEventPublisher>(relaxed = true)
        val session = FIXSession("FIX-02", "JPMorgan", FIXSessionStatus.DISCONNECTED, null, 0, 0)
        coEvery { fixSessionRepository.findById("FIX-02") } returns session
        coEvery { fixSessionRepository.updateStatus("FIX-02", FIXSessionStatus.CONNECTED) } returns Unit

        testApplication {
            application { configureFixSessionTestApp(fixSessionRepository, publisher) }
            val response = client.patch("/api/v1/fix/sessions/FIX-02/status") {
                contentType(ContentType.Application.Json)
                setBody("""{"status": "CONNECTED"}""")
            }
            response.status shouldBe HttpStatusCode.NoContent
            coVerify(exactly = 0) { publisher.publishDisconnected(any()) }
        }
    }

    test("PATCH session status returns 404 when session not found") {
        coEvery { fixSessionRepository.findById("UNKNOWN") } returns null

        testApplication {
            application { configureFixSessionTestApp(fixSessionRepository) }
            val response = client.patch("/api/v1/fix/sessions/UNKNOWN/status") {
                contentType(ContentType.Application.Json)
                setBody("""{"status": "DISCONNECTED"}""")
            }
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("PATCH session status returns 400 for invalid status") {
        testApplication {
            application { configureFixSessionTestApp(fixSessionRepository) }
            val response = client.patch("/api/v1/fix/sessions/FIX-01/status") {
                contentType(ContentType.Application.Json)
                setBody("""{"status": "INVALID"}""")
            }
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
