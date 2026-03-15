package com.kinetix.gateway.routes

import com.kinetix.gateway.client.JobPhaseItem
import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.client.ValuationJobDetailItem
import com.kinetix.gateway.client.ValuationJobSummaryItem
import com.kinetix.gateway.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*
import java.time.Instant

private val JOB = ValuationJobSummaryItem(
    jobId = "job-1",
    portfolioId = "port-1",
    triggerType = "ON_DEMAND",
    status = "COMPLETED",
    startedAt = Instant.parse("2025-01-15T10:00:00Z"),
    completedAt = Instant.parse("2025-01-15T10:00:00.150Z"),
    durationMs = 150,
    calculationType = "PARAMETRIC",
    varValue = 5000.0,
    expectedShortfall = 6250.0,
    pvValue = 1_800_000.0,
    delta = 1500.8,
    gamma = 61.0,
    vega = 5001.0,
    theta = -120.5,
    rho = 200.0,
)

class JobHistoryRoutesTest : FunSpec({

    val riskClient = mockk<RiskServiceClient>()

    beforeEach { clearMocks(riskClient) }

    test("forwards from and to query parameters to the client") {
        val from = Instant.parse("2025-01-15T09:00:00Z")
        val to = Instant.parse("2025-01-15T11:00:00Z")
        coEvery { riskClient.listValuationJobs("port-1", 20, 0, from, to) } returns Pair(listOf(JOB), 1L)

        testApplication {
            application { module(riskClient) }

            val response = client.get("/api/v1/risk/jobs/port-1?from=2025-01-15T09:00:00Z&to=2025-01-15T11:00:00Z")

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "\"items\""
            body shouldContain "\"totalCount\":1"
            coVerify { riskClient.listValuationJobs("port-1", 20, 0, from, to) }
        }
    }

    test("passes null for from and to when not provided") {
        coEvery { riskClient.listValuationJobs("port-1", 20, 0, null, null) } returns Pair(listOf(JOB), 1L)

        testApplication {
            application { module(riskClient) }

            val response = client.get("/api/v1/risk/jobs/port-1")

            response.status shouldBe HttpStatusCode.OK
            coVerify { riskClient.listValuationJobs("port-1", 20, 0, null, null) }
        }
    }

    test("returns 400 for invalid from timestamp") {
        testApplication {
            application { module(riskClient) }

            val response = client.get("/api/v1/risk/jobs/port-1?from=not-a-timestamp")

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("returns job detail with phases for a known job") {
        val detail = ValuationJobDetailItem(
            jobId = "11111111-1111-1111-1111-111111111111",
            portfolioId = "port-1",
            triggerType = "ON_DEMAND",
            status = "COMPLETED",
            startedAt = Instant.parse("2025-01-15T10:00:00Z"),
            completedAt = Instant.parse("2025-01-15T10:00:00.150Z"),
            durationMs = 150,
            calculationType = "PARAMETRIC",
            confidenceLevel = "CL_95",
            varValue = 5000.0,
            expectedShortfall = 6250.0,
            pvValue = 1_800_000.0,
            phases = listOf(
                JobPhaseItem(
                    name = "FETCH_POSITIONS",
                    status = "COMPLETED",
                    startedAt = Instant.parse("2025-01-15T10:00:00Z"),
                    completedAt = Instant.parse("2025-01-15T10:00:00.020Z"),
                    durationMs = 20,
                    details = mapOf("positionCount" to "5"),
                    error = null,
                ),
            ),
            error = null,
            valuationDate = "2025-01-15",
        )
        coEvery { riskClient.getValuationJobDetail("11111111-1111-1111-1111-111111111111") } returns detail

        testApplication {
            application { module(riskClient) }

            val response = client.get("/api/v1/risk/jobs/detail/11111111-1111-1111-1111-111111111111")

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "FETCH_POSITIONS"
            body shouldContain "positionCount"
            body shouldContain "CL_95"
            body shouldContain "5000.0"
            body shouldContain "2025-01-15"
        }
    }

    test("returns 404 for unknown job detail") {
        coEvery { riskClient.getValuationJobDetail("99999999-9999-9999-9999-999999999999") } returns null

        testApplication {
            application { module(riskClient) }

            val response = client.get("/api/v1/risk/jobs/detail/99999999-9999-9999-9999-999999999999")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
