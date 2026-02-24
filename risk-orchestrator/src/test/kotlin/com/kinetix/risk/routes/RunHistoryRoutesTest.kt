package com.kinetix.risk.routes

import com.kinetix.risk.model.*
import com.kinetix.risk.service.CalculationRunRecorder
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

private val RUN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111")
private val RUN_ID_2 = UUID.fromString("22222222-2222-2222-2222-222222222222")

private fun completedRun(
    runId: UUID = RUN_ID,
    portfolioId: String = "port-1",
    startedAt: Instant = Instant.parse("2025-01-15T10:00:00Z"),
) = CalculationRun(
    runId = runId,
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
        PipelineStep(
            name = PipelineStepName.FETCH_POSITIONS,
            status = RunStatus.COMPLETED,
            startedAt = startedAt,
            completedAt = startedAt.plusMillis(20),
            durationMs = 20,
            details = mapOf("positionCount" to 5),
        ),
    ),
)

class RunHistoryRoutesTest : FunSpec({

    val runRecorder = mockk<CalculationRunRecorder>()

    beforeEach {
        clearMocks(runRecorder)
    }

    test("lists calculation runs for a portfolio") {
        val runs = listOf(
            completedRun(runId = RUN_ID),
            completedRun(runId = RUN_ID_2, startedAt = Instant.parse("2025-01-15T09:00:00Z")),
        )
        coEvery { runRecorder.findByPortfolioId("port-1", 20, 0) } returns runs

        testApplication {
            install(ContentNegotiation) { json() }
            routing { runHistoryRoutes(runRecorder) }

            val response = client.get("/api/v1/risk/runs/port-1")

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain RUN_ID.toString()
            body shouldContain RUN_ID_2.toString()
            body shouldContain "ON_DEMAND"
            body shouldContain "COMPLETED"
        }
    }

    test("returns run detail with pipeline steps") {
        val run = completedRun()
        coEvery { runRecorder.findByRunId(RUN_ID) } returns run

        testApplication {
            install(ContentNegotiation) { json() }
            routing { runHistoryRoutes(runRecorder) }

            val response = client.get("/api/v1/risk/runs/detail/$RUN_ID")

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "FETCH_POSITIONS"
            body shouldContain "positionCount"
            body shouldContain "CL_95"
            body shouldContain "5000.0"
        }
    }

    test("returns 404 for unknown run ID") {
        val unknownId = UUID.fromString("99999999-9999-9999-9999-999999999999")
        coEvery { runRecorder.findByRunId(unknownId) } returns null

        testApplication {
            install(ContentNegotiation) { json() }
            routing { runHistoryRoutes(runRecorder) }

            val response = client.get("/api/v1/risk/runs/detail/$unknownId")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("supports limit and offset query parameters") {
        coEvery { runRecorder.findByPortfolioId("port-1", 5, 10) } returns emptyList()

        testApplication {
            install(ContentNegotiation) { json() }
            routing { runHistoryRoutes(runRecorder) }

            val response = client.get("/api/v1/risk/runs/port-1?limit=5&offset=10")

            response.status shouldBe HttpStatusCode.OK
            coVerify { runRecorder.findByPortfolioId("port-1", 5, 10) }
        }
    }
})
