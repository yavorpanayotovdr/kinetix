package com.kinetix.risk.routes

import com.kinetix.risk.client.CVAResult
import com.kinetix.risk.client.ClientResponse
import com.kinetix.risk.client.CounterpartyRiskClient
import com.kinetix.risk.client.PFEPositionInput
import com.kinetix.risk.client.PFEResult
import com.kinetix.risk.client.ReferenceDataServiceClient
import com.kinetix.risk.client.dtos.CounterpartyDto
import com.kinetix.risk.client.dtos.NettingAgreementDto
import com.kinetix.risk.model.CounterpartyExposureSnapshot
import com.kinetix.risk.model.ExposureAtTenor
import com.kinetix.risk.persistence.CounterpartyExposureRepository
import com.kinetix.risk.routes.dtos.CounterpartyExposureResponse
import com.kinetix.risk.routes.dtos.CVAResponse
import com.kinetix.risk.service.CounterpartyRiskOrchestrationService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import java.time.Instant

private val TENORS = listOf(
    ExposureAtTenor("1M", 1.0 / 12, 500_000.0, 750_000.0, 900_000.0),
    ExposureAtTenor("1Y", 1.0, 1_200_000.0, 1_800_000.0, 2_100_000.0),
)

private fun snapshot(
    counterpartyId: String = "CP-GS",
    netExposure: Double = 2_000_000.0,
    peakPfe: Double = 1_800_000.0,
    cva: Double? = 12_500.0,
) = CounterpartyExposureSnapshot(
    id = 1L,
    counterpartyId = counterpartyId,
    calculatedAt = Instant.parse("2026-03-24T10:00:00Z"),
    pfeProfile = TENORS,
    currentNetExposure = netExposure,
    peakPfe = peakPfe,
    cva = cva,
    cvaEstimated = false,
)

private fun Application.configureTestApp(service: CounterpartyRiskOrchestrationService) {
    install(ContentNegotiation) { json() }
    routing {
        counterpartyRiskRoutes(service)
    }
}

class CounterpartyRiskRoutesAcceptanceTest : FunSpec({

    val referenceDataClient = mockk<ReferenceDataServiceClient>()
    val counterpartyRiskClient = mockk<CounterpartyRiskClient>()
    val repository = mockk<CounterpartyExposureRepository>()
    val service = CounterpartyRiskOrchestrationService(
        referenceDataClient = referenceDataClient,
        counterpartyRiskClient = counterpartyRiskClient,
        repository = repository,
    )

    beforeEach {
        clearMocks(referenceDataClient, counterpartyRiskClient, repository)
    }

    test("GET /api/v1/counterparty-risk/ returns all latest exposures") {
        coEvery { repository.findLatestForAllCounterparties() } returns listOf(
            snapshot("CP-GS"),
            snapshot("CP-JPM"),
        )

        testApplication {
            application { configureTestApp(service) }
            val response = client.get("/api/v1/counterparty-risk/")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.decodeFromString<List<CounterpartyExposureResponse>>(response.bodyAsText())
            body.size shouldBe 2
            body.map { it.counterpartyId }.toSet() shouldBe setOf("CP-GS", "CP-JPM")
        }
    }

    test("GET /api/v1/counterparty-risk/{id} returns latest snapshot for counterparty") {
        coEvery { repository.findLatestByCounterpartyId("CP-GS") } returns snapshot("CP-GS")

        testApplication {
            application { configureTestApp(service) }
            val response = client.get("/api/v1/counterparty-risk/CP-GS")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.decodeFromString<CounterpartyExposureResponse>(response.bodyAsText())
            body.counterpartyId shouldBe "CP-GS"
            body.currentNetExposure shouldBe 2_000_000.0
            body.peakPfe shouldBe 1_800_000.0
            body.pfeProfile.size shouldBe 2
        }
    }

    test("GET /api/v1/counterparty-risk/{id} returns 404 when no snapshot exists") {
        coEvery { repository.findLatestByCounterpartyId("CP-NEW") } returns null

        testApplication {
            application { configureTestApp(service) }
            val response = client.get("/api/v1/counterparty-risk/CP-NEW")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/counterparty-risk/{id}/history returns historical snapshots") {
        val history = listOf(snapshot("CP-GS", netExposure = 2_000_000.0), snapshot("CP-GS", netExposure = 1_800_000.0))
        coEvery { repository.findByCounterpartyId("CP-GS", 90) } returns history

        testApplication {
            application { configureTestApp(service) }
            val response = client.get("/api/v1/counterparty-risk/CP-GS/history")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.decodeFromString<List<CounterpartyExposureResponse>>(response.bodyAsText())
            body.size shouldBe 2
        }
    }

    test("POST /api/v1/counterparty-risk/{id}/pfe computes and returns PFE snapshot") {
        coEvery { referenceDataClient.getCounterparty("CP-GS") } returns ClientResponse.Success(
            CounterpartyDto(counterpartyId = "CP-GS", legalName = "Goldman Sachs", lgd = 0.4)
        )
        coEvery { referenceDataClient.getNettingAgreements("CP-GS") } returns ClientResponse.Success(
            listOf(NettingAgreementDto("NS-001", "CP-GS", "ISDA_2002", true, 0.0, "USD"))
        )
        coEvery {
            counterpartyRiskClient.calculatePFE(
                counterpartyId = "CP-GS",
                nettingSetId = "NS-001",
                agreementType = "ISDA_2002",
                positions = any(),
                numSimulations = 0,
                seed = 0,
            )
        } returns PFEResult("CP-GS", "NS-001", 3_000_000.0, 2_000_000.0, TENORS)
        coEvery { repository.save(any()) } answers { args[0] as CounterpartyExposureSnapshot }

        testApplication {
            application { configureTestApp(service) }
            val response = client.post("/api/v1/counterparty-risk/CP-GS/pfe") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                        "positions": [
                            {"instrumentId": "AAPL", "marketValue": 1000000.0, "assetClass": "EQUITY", "volatility": 0.25, "sector": "TECHNOLOGY"}
                        ]
                    }
                    """.trimIndent()
                )
            }
            response.status shouldBe HttpStatusCode.OK
            val body = Json.decodeFromString<CounterpartyExposureResponse>(response.bodyAsText())
            body.counterpartyId shouldBe "CP-GS"
            body.currentNetExposure shouldBe 2_000_000.0
        }
    }

    test("POST /api/v1/counterparty-risk/{id}/pfe returns 400 when counterparty not found") {
        coEvery { referenceDataClient.getCounterparty("CP-MISSING") } returns ClientResponse.NotFound(404)

        testApplication {
            application { configureTestApp(service) }
            val response = client.post("/api/v1/counterparty-risk/CP-MISSING/pfe") {
                contentType(ContentType.Application.Json)
                setBody("""{"positions": []}""")
            }
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /api/v1/counterparty-risk/{id}/cva returns 404 when no PFE snapshot exists") {
        coEvery { repository.findLatestByCounterpartyId("CP-NEW") } returns null

        testApplication {
            application { configureTestApp(service) }
            val response = client.post("/api/v1/counterparty-risk/CP-NEW/cva")
            response.status shouldBe HttpStatusCode.NotFound
            response.bodyAsText() shouldContain "No PFE snapshot"
        }
    }

    test("POST /api/v1/counterparty-risk/{id}/cva computes CVA from latest PFE profile") {
        coEvery { repository.findLatestByCounterpartyId("CP-GS") } returns snapshot("CP-GS")
        coEvery { referenceDataClient.getCounterparty("CP-GS") } returns ClientResponse.Success(
            CounterpartyDto(
                counterpartyId = "CP-GS",
                legalName = "Goldman Sachs",
                lgd = 0.4,
                cdsSpreadBps = 65.0,
                ratingSp = "A+",
                sector = "FINANCIALS",
            )
        )
        coEvery {
            counterpartyRiskClient.calculateCVA(
                counterpartyId = "CP-GS",
                exposureProfile = TENORS,
                lgd = 0.4,
                pd1y = 0.0,
                cdsSpreadBps = 65.0,
                rating = "A+",
                sector = "FINANCIALS",
                riskFreeRate = 0.0,
            )
        } returns CVAResult("CP-GS", 12_500.0, false, 0.0065, 0.0065)

        testApplication {
            application { configureTestApp(service) }
            val response = client.post("/api/v1/counterparty-risk/CP-GS/cva")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.decodeFromString<CVAResponse>(response.bodyAsText())
            body.counterpartyId shouldBe "CP-GS"
            body.cva shouldBe 12_500.0
            body.isEstimated shouldBe false
        }
    }
})
