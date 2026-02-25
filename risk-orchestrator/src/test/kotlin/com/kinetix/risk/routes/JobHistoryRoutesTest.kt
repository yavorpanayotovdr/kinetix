package com.kinetix.risk.routes

import com.kinetix.risk.model.*
import com.kinetix.risk.service.ValuationJobRecorder
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
import java.util.UUID

private val JOB_ID = UUID.fromString("11111111-1111-1111-1111-111111111111")
private val JOB_ID_2 = UUID.fromString("22222222-2222-2222-2222-222222222222")

private fun completedJob(
    jobId: UUID = JOB_ID,
    portfolioId: String = "port-1",
    startedAt: Instant = Instant.parse("2025-01-15T10:00:00Z"),
) = ValuationJob(
    jobId = jobId,
    portfolioId = portfolioId,
    triggerType = TriggerType.ON_DEMAND,
    status = RunStatus.COMPLETED,
    startedAt = startedAt,
    completedAt = startedAt.plusMillis(150),
    durationMs = 150,
    calculationType = "PARAMETRIC",
    confidenceLevel = "CL_95",
    varValue = 5000.0,
    expectedShortfall = 6250.0,
    steps = listOf(
        JobStep(
            name = JobStepName.FETCH_POSITIONS,
            status = RunStatus.COMPLETED,
            startedAt = startedAt,
            completedAt = startedAt.plusMillis(20),
            durationMs = 20,
            details = mapOf("positionCount" to 5),
        ),
    ),
)

class JobHistoryRoutesTest : FunSpec({

    val jobRecorder = mockk<ValuationJobRecorder>()

    beforeEach {
        clearMocks(jobRecorder)
    }

    test("lists valuation jobs for a portfolio") {
        val jobs = listOf(
            completedJob(jobId = JOB_ID),
            completedJob(jobId = JOB_ID_2, startedAt = Instant.parse("2025-01-15T09:00:00Z")),
        )
        coEvery { jobRecorder.findByPortfolioId("port-1", 20, 0) } returns jobs

        testApplication {
            install(ContentNegotiation) { json() }
            routing { jobHistoryRoutes(jobRecorder) }

            val response = client.get("/api/v1/risk/jobs/port-1")

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain JOB_ID.toString()
            body shouldContain JOB_ID_2.toString()
            body shouldContain "ON_DEMAND"
            body shouldContain "COMPLETED"
        }
    }

    test("returns job detail with job steps") {
        val job = completedJob()
        coEvery { jobRecorder.findByJobId(JOB_ID) } returns job

        testApplication {
            install(ContentNegotiation) { json() }
            routing { jobHistoryRoutes(jobRecorder) }

            val response = client.get("/api/v1/risk/jobs/detail/$JOB_ID")

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "FETCH_POSITIONS"
            body shouldContain "positionCount"
            body shouldContain "CL_95"
            body shouldContain "5000.0"
        }
    }

    test("returns 404 for unknown job ID") {
        val unknownId = UUID.fromString("99999999-9999-9999-9999-999999999999")
        coEvery { jobRecorder.findByJobId(unknownId) } returns null

        testApplication {
            install(ContentNegotiation) { json() }
            routing { jobHistoryRoutes(jobRecorder) }

            val response = client.get("/api/v1/risk/jobs/detail/$unknownId")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("supports limit and offset query parameters") {
        coEvery { jobRecorder.findByPortfolioId("port-1", 5, 10) } returns emptyList()

        testApplication {
            install(ContentNegotiation) { json() }
            routing { jobHistoryRoutes(jobRecorder) }

            val response = client.get("/api/v1/risk/jobs/port-1?limit=5&offset=10")

            response.status shouldBe HttpStatusCode.OK
            coVerify { jobRecorder.findByPortfolioId("port-1", 5, 10) }
        }
    }

    test("filters jobs by from and to") {
        val from = Instant.parse("2025-01-15T09:00:00Z")
        val to = Instant.parse("2025-01-15T11:00:00Z")
        coEvery { jobRecorder.findByPortfolioId("port-1", 20, 0, from, to) } returns listOf(completedJob())

        testApplication {
            install(ContentNegotiation) { json() }
            routing { jobHistoryRoutes(jobRecorder) }

            val response = client.get("/api/v1/risk/jobs/port-1?from=2025-01-15T09:00:00Z&to=2025-01-15T11:00:00Z")

            response.status shouldBe HttpStatusCode.OK
            coVerify { jobRecorder.findByPortfolioId("port-1", 20, 0, from, to) }
        }
    }

    test("returns 400 for invalid from timestamp") {
        testApplication {
            install(ContentNegotiation) { json() }
            routing { jobHistoryRoutes(jobRecorder) }

            val response = client.get("/api/v1/risk/jobs/port-1?from=not-a-timestamp")

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("returns 400 for invalid to timestamp") {
        testApplication {
            install(ContentNegotiation) { json() }
            routing { jobHistoryRoutes(jobRecorder) }

            val response = client.get("/api/v1/risk/jobs/port-1?to=garbage")

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
