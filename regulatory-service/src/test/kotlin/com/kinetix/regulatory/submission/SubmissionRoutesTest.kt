package com.kinetix.regulatory.submission

import com.kinetix.regulatory.client.RiskOrchestratorClient
import com.kinetix.regulatory.module
import com.kinetix.regulatory.persistence.FrtbCalculationRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import java.util.UUID

class SubmissionRoutesTest : FunSpec({

    val repository = mockk<SubmissionRepository>()
    val service = SubmissionService(repository)

    test("POST /api/v1/submissions creates a new submission") {
        coEvery { repository.save(any()) } returns Unit

        testApplication {
            application {
                module(mockk<FrtbCalculationRepository>(), mockk<RiskOrchestratorClient>())
                routing { submissionRoutes(service) }
            }
            val response = client.post("/api/v1/submissions") {
                contentType(ContentType.Application.Json)
                setBody("""{"reportType":"FRTB_SBM","preparerId":"analyst-1","deadline":"2026-03-31T23:59:59Z"}""")
            }
            response.status shouldBe HttpStatusCode.Created
            val body = response.bodyAsText()
            body shouldContain "\"reportType\":\"FRTB_SBM\""
            body shouldContain "\"status\":\"DRAFT\""
        }
    }

    test("GET /api/v1/submissions lists all submissions") {
        coEvery { repository.findAll() } returns listOf(
            RegulatorySubmission(
                id = UUID.randomUUID().toString(),
                reportType = "FRTB_SBM",
                status = SubmissionStatus.DRAFT,
                preparerId = "analyst-1",
                approverId = null,
                deadline = Instant.parse("2026-03-31T23:59:59Z"),
                submittedAt = null,
                acknowledgedAt = null,
                createdAt = Instant.now(),
            ),
        )

        testApplication {
            application {
                module(mockk<FrtbCalculationRepository>(), mockk<RiskOrchestratorClient>())
                routing { submissionRoutes(service) }
            }
            val response = client.get("/api/v1/submissions")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "\"reportType\":\"FRTB_SBM\""
        }
    }

    test("PATCH /api/v1/submissions/{id}/review transitions to PENDING_REVIEW") {
        val id = UUID.randomUUID().toString()
        val submission = RegulatorySubmission(
            id = id,
            reportType = "FRTB_SBM",
            status = SubmissionStatus.DRAFT,
            preparerId = "analyst-1",
            approverId = null,
            deadline = Instant.parse("2026-03-31T23:59:59Z"),
            submittedAt = null,
            acknowledgedAt = null,
            createdAt = Instant.now(),
        )
        coEvery { repository.findById(id) } returns submission
        coEvery { repository.save(any()) } returns Unit

        testApplication {
            application {
                module(mockk<FrtbCalculationRepository>(), mockk<RiskOrchestratorClient>())
                routing { submissionRoutes(service) }
            }
            val response = client.patch("/api/v1/submissions/$id/review")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "\"status\":\"PENDING_REVIEW\""
        }
    }

    test("PATCH /api/v1/submissions/{id}/approve enforces four-eyes") {
        val id = UUID.randomUUID().toString()
        val submission = RegulatorySubmission(
            id = id,
            reportType = "FRTB_SBM",
            status = SubmissionStatus.PENDING_REVIEW,
            preparerId = "analyst-1",
            approverId = null,
            deadline = Instant.parse("2026-03-31T23:59:59Z"),
            submittedAt = null,
            acknowledgedAt = null,
            createdAt = Instant.now(),
        )
        coEvery { repository.findById(id) } returns submission

        testApplication {
            application {
                module(mockk<FrtbCalculationRepository>(), mockk<RiskOrchestratorClient>())
                routing { submissionRoutes(service) }
            }
            val response = client.patch("/api/v1/submissions/$id/approve") {
                contentType(ContentType.Application.Json)
                setBody("""{"approverId":"analyst-1"}""")
            }
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
