package com.kinetix.risk.routes

import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.SnapshotType
import com.kinetix.risk.model.SodBaselineStatus
import com.kinetix.risk.routes.dtos.SodBaselineStatusResponse
import com.kinetix.risk.service.SodSnapshotService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

class SodSnapshotRouteAcceptanceTest : FunSpec({

    val sodSnapshotService = mockk<SodSnapshotService>()

    beforeEach {
        clearMocks(sodSnapshotService)
    }

    test("GET /api/v1/risk/sod-snapshot/{portfolioId}/status returns baseline status when it exists") {
        coEvery { sodSnapshotService.getBaselineStatus(any(), any()) } returns SodBaselineStatus(
            exists = true,
            baselineDate = "2025-01-15",
            snapshotType = SnapshotType.MANUAL,
            createdAt = Instant.parse("2025-01-15T08:00:00Z"),
            sourceJobId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
            calculationType = "PARAMETRIC",
        )

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                get("/api/v1/risk/sod-snapshot/{portfolioId}/status") {
                    val portfolioId = call.parameters["portfolioId"]!!
                    val status = sodSnapshotService.getBaselineStatus(
                        PortfolioId(portfolioId),
                        LocalDate.now(),
                    )
                    call.respond(status.toResponse())
                }
            }

            val response = client.get("/api/v1/risk/sod-snapshot/port-1/status")
            response.status shouldBe HttpStatusCode.OK

            val body = Json.decodeFromString<SodBaselineStatusResponse>(response.bodyAsText())
            body.exists shouldBe true
            body.baselineDate shouldBe "2025-01-15"
            body.snapshotType shouldBe "MANUAL"
            body.createdAt shouldBe "2025-01-15T08:00:00Z"
            body.sourceJobId shouldBe "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
            body.calculationType shouldBe "PARAMETRIC"
        }
    }

    test("GET /api/v1/risk/sod-snapshot/{portfolioId}/status returns exists=false when no baseline") {
        coEvery { sodSnapshotService.getBaselineStatus(any(), any()) } returns SodBaselineStatus(
            exists = false,
        )

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                get("/api/v1/risk/sod-snapshot/{portfolioId}/status") {
                    val portfolioId = call.parameters["portfolioId"]!!
                    val status = sodSnapshotService.getBaselineStatus(
                        PortfolioId(portfolioId),
                        LocalDate.now(),
                    )
                    call.respond(status.toResponse())
                }
            }

            val response = client.get("/api/v1/risk/sod-snapshot/port-1/status")
            response.status shouldBe HttpStatusCode.OK

            val body = Json.decodeFromString<SodBaselineStatusResponse>(response.bodyAsText())
            body.exists shouldBe false
            body.baselineDate shouldBe null
            body.snapshotType shouldBe null
            body.createdAt shouldBe null
        }
    }

    test("POST /api/v1/risk/sod-snapshot/{portfolioId} creates manual snapshot and returns 201") {
        coEvery { sodSnapshotService.createSnapshot(any(), any(), any(), any()) } just Runs
        coEvery { sodSnapshotService.getBaselineStatus(any(), any()) } returns SodBaselineStatus(
            exists = true,
            baselineDate = "2025-01-15",
            snapshotType = SnapshotType.MANUAL,
            createdAt = Instant.parse("2025-01-15T09:30:00Z"),
        )

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                post("/api/v1/risk/sod-snapshot/{portfolioId}") {
                    val portfolioId = call.parameters["portfolioId"]!!
                    val today = LocalDate.now()
                    sodSnapshotService.createSnapshot(
                        PortfolioId(portfolioId),
                        SnapshotType.MANUAL,
                        date = today,
                    )
                    val status = sodSnapshotService.getBaselineStatus(PortfolioId(portfolioId), today)
                    call.response.status(HttpStatusCode.Created)
                    call.respond(status.toResponse())
                }
            }

            val response = client.post("/api/v1/risk/sod-snapshot/port-1")
            response.status shouldBe HttpStatusCode.Created

            val body = Json.decodeFromString<SodBaselineStatusResponse>(response.bodyAsText())
            body.exists shouldBe true
            body.snapshotType shouldBe "MANUAL"
        }

        coVerify { sodSnapshotService.createSnapshot(PortfolioId("port-1"), SnapshotType.MANUAL, any(), any()) }
    }

    test("DELETE /api/v1/risk/sod-snapshot/{portfolioId} resets baseline and returns 204") {
        coEvery { sodSnapshotService.resetBaseline(any(), any()) } just Runs

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                delete("/api/v1/risk/sod-snapshot/{portfolioId}") {
                    val portfolioId = call.parameters["portfolioId"]!!
                    sodSnapshotService.resetBaseline(PortfolioId(portfolioId), LocalDate.now())
                    call.response.status(HttpStatusCode.NoContent)
                    call.respond("")
                }
            }

            val response = client.delete("/api/v1/risk/sod-snapshot/port-1")
            response.status shouldBe HttpStatusCode.NoContent
        }

        coVerify { sodSnapshotService.resetBaseline(PortfolioId("port-1"), any()) }
    }
})
