package com.kinetix.risk.routes

import com.kinetix.risk.model.*
import com.kinetix.risk.persistence.DatabaseTestSetup
import com.kinetix.risk.persistence.ExposedValuationJobRecorder
import com.kinetix.risk.persistence.ValuationJobsTable
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.UUID

private fun completedJob(
    startedAt: Instant = Instant.parse("2025-01-15T10:00:00Z"),
) = ValuationJob(
    jobId = UUID.randomUUID(),
    portfolioId = "port-1",
    triggerType = TriggerType.ON_DEMAND,
    status = RunStatus.COMPLETED,
    startedAt = startedAt,
    completedAt = startedAt.plusMillis(150),
    durationMs = 150,
    calculationType = "PARAMETRIC",
    confidenceLevel = "CL_95",
    varValue = 5000.0,
    expectedShortfall = 6250.0,
    pvValue = 1_800_000.0,
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

class JobHistoryRoutesIntegrationTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val recorder = ExposedValuationJobRecorder(db)

    beforeEach {
        newSuspendedTransaction(db = db) { ValuationJobsTable.deleteAll() }
    }

    test("filters jobs by from and to through the HTTP route") {
        val earlyJob = completedJob(startedAt = Instant.parse("2025-01-15T08:00:00Z"))
        val middleJob = completedJob(startedAt = Instant.parse("2025-01-15T10:00:00Z"))
        val lateJob = completedJob(startedAt = Instant.parse("2025-01-15T12:00:00Z"))

        recorder.save(earlyJob)
        recorder.save(middleJob)
        recorder.save(lateJob)

        testApplication {
            install(ContentNegotiation) { json() }
            routing { jobHistoryRoutes(recorder) }

            // Request only jobs between 09:00 and 11:00 — should return only middleJob
            val response = client.get("/api/v1/risk/jobs/port-1?from=2025-01-15T09:00:00Z&to=2025-01-15T11:00:00Z")

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain middleJob.jobId.toString()
            body shouldNotContain earlyJob.jobId.toString()
            body shouldNotContain lateJob.jobId.toString()
        }
    }

    test("returns all jobs when from and to are not provided") {
        val job1 = completedJob(startedAt = Instant.parse("2025-01-15T08:00:00Z"))
        val job2 = completedJob(startedAt = Instant.parse("2025-01-15T12:00:00Z"))

        recorder.save(job1)
        recorder.save(job2)

        testApplication {
            install(ContentNegotiation) { json() }
            routing { jobHistoryRoutes(recorder) }

            val response = client.get("/api/v1/risk/jobs/port-1")

            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain job1.jobId.toString()
            body shouldContain job2.jobId.toString()
        }
    }

    test("returns 400 for invalid from timestamp") {
        testApplication {
            install(ContentNegotiation) { json() }
            routing { jobHistoryRoutes(recorder) }

            val response = client.get("/api/v1/risk/jobs/port-1?from=not-a-date")

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
