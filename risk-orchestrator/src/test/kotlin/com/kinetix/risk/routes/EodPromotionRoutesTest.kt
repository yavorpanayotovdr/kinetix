package com.kinetix.risk.routes

import com.kinetix.risk.model.*
import com.kinetix.risk.service.EodPromotionService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val JOB_ID = UUID.fromString("11111111-1111-1111-1111-111111111111")
private val VALUATION_DATE = LocalDate.of(2026, 3, 13)

private fun promotedJob() = ValuationJob(
    jobId = JOB_ID,
    portfolioId = "port-1",
    triggerType = TriggerType.ON_DEMAND,
    status = RunStatus.COMPLETED,
    startedAt = Instant.parse("2026-03-13T17:00:00Z"),
    valuationDate = VALUATION_DATE,
    completedAt = Instant.parse("2026-03-13T17:00:30Z"),
    durationMs = 30_000,
    varValue = 5000.0,
    expectedShortfall = 6250.0,
    runLabel = RunLabel.OFFICIAL_EOD,
    promotedAt = Instant.parse("2026-03-13T18:00:00Z"),
    promotedBy = "user-b",
)

class EodPromotionRoutesTest : FunSpec({

    val eodService = mockk<EodPromotionService>()

    beforeEach {
        clearMocks(eodService)
    }

    test("PATCH promote returns 200 with promotion response") {
        coEvery { eodService.promoteToOfficialEod(JOB_ID, "user-b") } returns promotedJob()

        testApplication {
            install(ContentNegotiation) { json() }
            routing { eodPromotionRoutes(eodService) }

            val response = client.patch("/api/v1/risk/jobs/$JOB_ID/label") {
                contentType(ContentType.Application.Json)
                setBody("""{"label":"OFFICIAL_EOD","promotedBy":"user-b"}""")
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "\"runLabel\":\"OFFICIAL_EOD\""
            body shouldContain "\"promotedBy\":\"user-b\""
            body shouldContain "\"portfolioId\":\"port-1\""
        }
    }

    test("PATCH promote returns 404 when job not found") {
        coEvery { eodService.promoteToOfficialEod(JOB_ID, "user-b") } throws
            EodPromotionException.JobNotFound(JOB_ID)

        testApplication {
            install(ContentNegotiation) { json() }
            routing { eodPromotionRoutes(eodService) }

            val response = client.patch("/api/v1/risk/jobs/$JOB_ID/label") {
                contentType(ContentType.Application.Json)
                setBody("""{"label":"OFFICIAL_EOD","promotedBy":"user-b"}""")
            }

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("PATCH promote returns 400 when job not completed") {
        coEvery { eodService.promoteToOfficialEod(JOB_ID, "user-b") } throws
            EodPromotionException.JobNotCompleted(JOB_ID)

        testApplication {
            install(ContentNegotiation) { json() }
            routing { eodPromotionRoutes(eodService) }

            val response = client.patch("/api/v1/risk/jobs/$JOB_ID/label") {
                contentType(ContentType.Application.Json)
                setBody("""{"label":"OFFICIAL_EOD","promotedBy":"user-b"}""")
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("PATCH promote returns 403 for self-promotion") {
        coEvery { eodService.promoteToOfficialEod(JOB_ID, "user-a") } throws
            EodPromotionException.SelfPromotion("user-a")

        testApplication {
            install(ContentNegotiation) { json() }
            routing { eodPromotionRoutes(eodService) }

            val response = client.patch("/api/v1/risk/jobs/$JOB_ID/label") {
                contentType(ContentType.Application.Json)
                setBody("""{"label":"OFFICIAL_EOD","promotedBy":"user-a"}""")
            }

            response.status shouldBe HttpStatusCode.Forbidden
            response.bodyAsText() shouldContain "Separation of duties"
        }
    }

    test("PATCH promote returns 409 when already promoted") {
        coEvery { eodService.promoteToOfficialEod(JOB_ID, "user-b") } throws
            EodPromotionException.AlreadyPromoted(JOB_ID)

        testApplication {
            install(ContentNegotiation) { json() }
            routing { eodPromotionRoutes(eodService) }

            val response = client.patch("/api/v1/risk/jobs/$JOB_ID/label") {
                contentType(ContentType.Application.Json)
                setBody("""{"label":"OFFICIAL_EOD","promotedBy":"user-b"}""")
            }

            response.status shouldBe HttpStatusCode.Conflict
        }
    }

    test("PATCH promote returns 409 when conflicting Official EOD exists") {
        coEvery { eodService.promoteToOfficialEod(JOB_ID, "user-b") } throws
            EodPromotionException.ConflictingOfficialEod("port-1", "2026-03-13")

        testApplication {
            install(ContentNegotiation) { json() }
            routing { eodPromotionRoutes(eodService) }

            val response = client.patch("/api/v1/risk/jobs/$JOB_ID/label") {
                contentType(ContentType.Application.Json)
                setBody("""{"label":"OFFICIAL_EOD","promotedBy":"user-b"}""")
            }

            response.status shouldBe HttpStatusCode.Conflict
            response.bodyAsText() shouldContain "already exists"
        }
    }

    test("PATCH demote returns 200 when successful") {
        val demoted = promotedJob().copy(runLabel = null, promotedAt = null, promotedBy = null)
        coEvery { eodService.demoteFromOfficialEod(JOB_ID, "user-c") } returns demoted

        testApplication {
            install(ContentNegotiation) { json() }
            routing { eodPromotionRoutes(eodService) }

            val response = client.patch("/api/v1/risk/jobs/$JOB_ID/label") {
                contentType(ContentType.Application.Json)
                setBody("""{"label":"ADHOC","promotedBy":"user-c"}""")
            }

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "\"runLabel\":\"ADHOC\""
        }
    }

    test("PATCH returns 400 for invalid label") {
        testApplication {
            install(ContentNegotiation) { json() }
            routing { eodPromotionRoutes(eodService) }

            val response = client.patch("/api/v1/risk/jobs/$JOB_ID/label") {
                contentType(ContentType.Application.Json)
                setBody("""{"label":"INVALID","promotedBy":"user-b"}""")
            }

            response.status shouldBe HttpStatusCode.BadRequest
            response.bodyAsText() shouldContain "Invalid label"
        }
    }

    test("GET official-eod returns 200 when designation exists") {
        coEvery { eodService.findOfficialEod("port-1", VALUATION_DATE) } returns promotedJob()

        testApplication {
            install(ContentNegotiation) { json() }
            routing { eodPromotionRoutes(eodService) }

            val response = client.get("/api/v1/risk/jobs/port-1/official-eod?date=2026-03-13")

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "\"runLabel\":\"OFFICIAL_EOD\""
            body shouldContain JOB_ID.toString()
        }
    }

    test("GET official-eod returns 404 when no designation") {
        coEvery { eodService.findOfficialEod("port-1", VALUATION_DATE) } returns null

        testApplication {
            install(ContentNegotiation) { json() }
            routing { eodPromotionRoutes(eodService) }

            val response = client.get("/api/v1/risk/jobs/port-1/official-eod?date=2026-03-13")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET official-eod returns 400 when date parameter missing") {
        testApplication {
            install(ContentNegotiation) { json() }
            routing { eodPromotionRoutes(eodService) }

            val response = client.get("/api/v1/risk/jobs/port-1/official-eod")

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
