package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
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
})
