package com.kinetix.risk.routes

import com.kinetix.risk.model.RunLabel
import com.kinetix.risk.model.RunStatus
import com.kinetix.risk.model.TriggerType
import com.kinetix.risk.model.ValuationJob
import com.kinetix.risk.routes.dtos.EodTimelineResponse
import com.kinetix.risk.service.ValuationJobRecorder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val json = Json { ignoreUnknownKeys = true }

private fun eodJob(
    bookId: String = "port-1",
    valuationDate: LocalDate,
    varValue: Double = 10_000.0,
    expectedShortfall: Double = 12_500.0,
) = ValuationJob(
    jobId = UUID.randomUUID(),
    bookId = bookId,
    triggerType = TriggerType.SCHEDULED,
    status = RunStatus.COMPLETED,
    startedAt = valuationDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
    valuationDate = valuationDate,
    completedAt = valuationDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).plusSeconds(60),
    calculationType = "PARAMETRIC",
    confidenceLevel = "CL_99",
    varValue = varValue,
    expectedShortfall = expectedShortfall,
    pvValue = 1_000_000.0,
    delta = 0.75,
    gamma = 0.01,
    vega = 500.0,
    theta = -30.0,
    rho = 15.0,
    runLabel = RunLabel.OFFICIAL_EOD,
    promotedAt = Instant.parse("2025-01-15T18:00:00Z"),
    promotedBy = "risk-approver",
    triggeredBy = "SYSTEM",
)

class EodTimelineAcceptanceTest : FunSpec({

    val jobRecorder = mockk<ValuationJobRecorder>()

    beforeEach { clearMocks(jobRecorder) }

    fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            install(ContentNegotiation) { json(json) }
            routing { eodTimelineRoutes(jobRecorder) }
            block()
        }
    }

    test("returns rows for each business day in the requested range, ordered ascending by date") {
        val day1 = LocalDate.of(2025, 1, 13)
        val day2 = LocalDate.of(2025, 1, 14)
        val day3 = LocalDate.of(2025, 1, 15)

        coEvery {
            jobRecorder.findOfficialEodRange("port-1", day1, day3)
        } returns listOf(
            eodJob(valuationDate = day1, varValue = 9_000.0),
            eodJob(valuationDate = day2, varValue = 10_000.0),
            eodJob(valuationDate = day3, varValue = 11_000.0),
        )

        testApp {
            val response = client.get("/api/v1/risk/eod-timeline/port-1?from=2025-01-13&to=2025-01-15")

            response.status shouldBe HttpStatusCode.OK

            val body = json.decodeFromString<EodTimelineResponse>(response.bodyAsText())
            body.bookId shouldBe "port-1"
            body.from shouldBe "2025-01-13"
            body.to shouldBe "2025-01-15"
            body.entries.size shouldBe 3
            body.entries[0].valuationDate shouldBe "2025-01-13"
            body.entries[1].valuationDate shouldBe "2025-01-14"
            body.entries[2].valuationDate shouldBe "2025-01-15"
        }
    }

    test("computes varChange as difference from previous business day VaR") {
        val day1 = LocalDate.of(2025, 1, 14)
        val day2 = LocalDate.of(2025, 1, 15)

        coEvery {
            jobRecorder.findOfficialEodRange("port-1", day1, day2)
        } returns listOf(
            eodJob(valuationDate = day1, varValue = 8_000.0),
            eodJob(valuationDate = day2, varValue = 10_000.0),
        )

        testApp {
            val response = client.get("/api/v1/risk/eod-timeline/port-1?from=2025-01-14&to=2025-01-15")

            response.status shouldBe HttpStatusCode.OK

            val body = json.decodeFromString<EodTimelineResponse>(response.bodyAsText())
            body.entries[1].varChange shouldBe 2_000.0
        }
    }

    test("returns varChange null for the first row in the range") {
        val day = LocalDate.of(2025, 1, 15)

        coEvery {
            jobRecorder.findOfficialEodRange("port-1", day, day)
        } returns listOf(
            eodJob(valuationDate = day, varValue = 10_000.0),
        )

        testApp {
            val response = client.get("/api/v1/risk/eod-timeline/port-1?from=2025-01-15&to=2025-01-15")

            response.status shouldBe HttpStatusCode.OK

            val body = json.decodeFromString<EodTimelineResponse>(response.bodyAsText())
            body.entries[0].varChange.shouldBeNull()
        }
    }

    test("returns 400 when from is after to") {
        testApp {
            val response = client.get("/api/v1/risk/eod-timeline/port-1?from=2025-01-20&to=2025-01-15")

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("returns 400 when date format is invalid") {
        testApp {
            val response = client.get("/api/v1/risk/eod-timeline/port-1?from=15-01-2025&to=2025-01-20")

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("returns 400 when date range exceeds 366 days") {
        testApp {
            val response = client.get("/api/v1/risk/eod-timeline/port-1?from=2023-01-01&to=2024-01-03")

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
