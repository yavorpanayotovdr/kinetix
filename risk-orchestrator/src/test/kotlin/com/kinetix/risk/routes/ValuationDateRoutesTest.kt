package com.kinetix.risk.routes

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.BookId
import com.kinetix.risk.cache.InMemoryVaRCache
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
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val TEST_DATE = LocalDate.of(2025, 3, 10)

private fun completedJob(
    bookId: String = "port-1",
    valuationDate: LocalDate = TEST_DATE,
    startedAt: Instant = Instant.parse("2025-03-10T14:00:00Z"),
    varValue: Double = 5000.0,
) = ValuationJob(
    jobId = UUID.randomUUID(),
    bookId = bookId,
    triggerType = TriggerType.ON_DEMAND,
    status = RunStatus.COMPLETED,
    startedAt = startedAt,
    valuationDate = valuationDate,
    completedAt = startedAt.plusMillis(150),
    durationMs = 150,
    calculationType = "PARAMETRIC",
    confidenceLevel = "CL_95",
    varValue = varValue,
    expectedShortfall = varValue * 1.25,
    pvValue = 1_800_000.0,
    componentBreakdownSnapshot = listOf(
        ComponentBreakdown(AssetClass.EQUITY, varValue, 100.0),
    ),
    positionRiskSnapshot = listOf(
        PositionRisk(
            instrumentId = InstrumentId("AAPL"),
            assetClass = AssetClass.EQUITY,
            marketValue = BigDecimal("17000.00"),
            delta = 0.85,
            gamma = 0.02,
            vega = null,
            varContribution = BigDecimal("5000.00"),
            esContribution = BigDecimal("6250.00"),
            percentageOfTotal = BigDecimal("100.00"),
        ),
    ),
    phases = emptyList(),
    triggeredBy = "user-a",
)

private fun cachedResult(bookId: String = "port-1") = ValuationResult(
    bookId = BookId(bookId),
    calculationType = CalculationType.PARAMETRIC,
    confidenceLevel = ConfidenceLevel.CL_95,
    varValue = 4200.0,
    expectedShortfall = 5250.0,
    componentBreakdown = listOf(ComponentBreakdown(AssetClass.EQUITY, 4200.0, 100.0)),
    greeks = null,
    calculatedAt = Instant.parse("2025-03-10T10:00:00Z"),
    computedOutputs = setOf(ValuationOutput.VAR, ValuationOutput.EXPECTED_SHORTFALL),
)

class ValuationDateRoutesTest : FunSpec({

    val jobRecorder = mockk<ValuationJobRecorder>()
    val varCalculationService = mockk<com.kinetix.risk.service.VaRCalculationService>()
    val positionProvider = mockk<com.kinetix.risk.client.PositionProvider>()
    val stressTestStub = mockk<com.kinetix.proto.risk.StressTestServiceGrpcKt.StressTestServiceCoroutineStub>()
    val regulatoryStub = mockk<com.kinetix.proto.risk.RegulatoryReportingServiceGrpcKt.RegulatoryReportingServiceCoroutineStub>()

    beforeEach {
        clearMocks(jobRecorder)
    }

    test("GET /risk/var without valuationDate returns from cache") {
        val varCache = InMemoryVaRCache()
        varCache.put("port-1", cachedResult())

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                riskRoutes(varCalculationService, varCache, positionProvider, stressTestStub, regulatoryStub, jobRecorder = jobRecorder)
            }

            val response = client.get("/api/v1/risk/var/port-1")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "\"varValue\":\"4200.00\""
        }
    }

    test("GET /risk/var with valuationDate returns historical snapshot from DB") {
        val job = completedJob()
        coEvery { jobRecorder.findLatestCompletedByDate("port-1", TEST_DATE) } returns job

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                riskRoutes(varCalculationService, InMemoryVaRCache(), positionProvider, stressTestStub, regulatoryStub, jobRecorder = jobRecorder)
            }

            val response = client.get("/api/v1/risk/var/port-1?valuationDate=2025-03-10")
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            body shouldContain "\"varValue\":\"5000.00\""
            body shouldContain "\"valuationDate\":\"2025-03-10\""
        }
    }

    test("GET /risk/var with invalid valuationDate returns 400") {
        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                riskRoutes(varCalculationService, InMemoryVaRCache(), positionProvider, stressTestStub, regulatoryStub, jobRecorder = jobRecorder)
            }

            val response = client.get("/api/v1/risk/var/port-1?valuationDate=banana")
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /risk/var with future valuationDate returns 404") {
        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                riskRoutes(varCalculationService, InMemoryVaRCache(), positionProvider, stressTestStub, regulatoryStub, jobRecorder = jobRecorder)
            }

            val response = client.get("/api/v1/risk/var/port-1?valuationDate=2099-12-31")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /risk/var with valuationDate but no completed job returns 404") {
        coEvery { jobRecorder.findLatestCompletedByDate("port-1", TEST_DATE) } returns null

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                riskRoutes(varCalculationService, InMemoryVaRCache(), positionProvider, stressTestStub, regulatoryStub, jobRecorder = jobRecorder)
            }

            val response = client.get("/api/v1/risk/var/port-1?valuationDate=2025-03-10")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /risk/positions with valuationDate returns historical position risk") {
        val job = completedJob()
        coEvery { jobRecorder.findLatestCompletedByDate("port-1", TEST_DATE) } returns job

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                riskRoutes(varCalculationService, InMemoryVaRCache(), positionProvider, stressTestStub, regulatoryStub, jobRecorder = jobRecorder)
            }

            val response = client.get("/api/v1/risk/positions/port-1?valuationDate=2025-03-10")
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "AAPL"
        }
    }

    test("GET /risk/positions with valuationDate but empty snapshot returns 404") {
        val job = completedJob().copy(positionRiskSnapshot = emptyList())
        coEvery { jobRecorder.findLatestCompletedByDate("port-1", TEST_DATE) } returns job

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                riskRoutes(varCalculationService, InMemoryVaRCache(), positionProvider, stressTestStub, regulatoryStub, jobRecorder = jobRecorder)
            }

            val response = client.get("/api/v1/risk/positions/port-1?valuationDate=2025-03-10")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
