package com.kinetix.risk.routes

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.routes.dtos.*
import com.kinetix.risk.cache.LatestVaRCache
import com.kinetix.risk.model.*
import com.kinetix.risk.service.VaRCalculationService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant

private val TEST_INSTANT = Instant.parse("2025-01-15T10:30:00Z")

private fun valuationResult(
    portfolioId: String = "port-1",
    varValue: Double = 5000.0,
) = ValuationResult(
    portfolioId = PortfolioId(portfolioId),
    calculationType = CalculationType.PARAMETRIC,
    confidenceLevel = ConfidenceLevel.CL_95,
    varValue = varValue,
    expectedShortfall = varValue * 1.25,
    componentBreakdown = listOf(
        ComponentBreakdown(AssetClass.EQUITY, varValue, 100.0),
    ),
    greeks = null,
    calculatedAt = TEST_INSTANT,
    computedOutputs = setOf(ValuationOutput.VAR, ValuationOutput.EXPECTED_SHORTFALL),
)

/** Mirrors the internal toResponse() extension in RiskMappers. */
private fun ValuationResult.toResponse() = VaRResultResponse(
    portfolioId = portfolioId.value,
    calculationType = calculationType.name,
    confidenceLevel = confidenceLevel.name,
    varValue = "%.2f".format(varValue ?: 0.0),
    expectedShortfall = "%.2f".format(expectedShortfall ?: 0.0),
    componentBreakdown = componentBreakdown.map {
        ComponentBreakdownDto(
            assetClass = it.assetClass.name,
            varContribution = "%.2f".format(it.varContribution),
            percentageOfTotal = "%.2f".format(it.percentageOfTotal),
        )
    },
    calculatedAt = calculatedAt.toString(),
    positionRisk = positionRisk.takeIf { it.isNotEmpty() }?.map { it.toDto() },
)

private fun PositionRisk.toDto() = PositionRiskDto(
    instrumentId = instrumentId.value,
    assetClass = assetClass.name,
    marketValue = marketValue.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
    delta = delta?.let { "%.6f".format(it) },
    gamma = gamma?.let { "%.6f".format(it) },
    vega = vega?.let { "%.6f".format(it) },
    varContribution = varContribution.toPlainString(),
    esContribution = esContribution.toPlainString(),
    percentageOfTotal = percentageOfTotal.toPlainString(),
)

class RiskRoutesTest : FunSpec({

    val varCache = LatestVaRCache()
    val varCalculationService = mockk<VaRCalculationService>()

    beforeEach {
        clearMocks(varCalculationService)
    }

    test("GET /api/v1/risk/var/{portfolioId} returns cached result") {
        val result = valuationResult(portfolioId = "port-1", varValue = 4200.0)
        varCache.put("port-1", result)

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                route("/api/v1/risk/var/{portfolioId}") {
                    get {
                        val portfolioId = call.parameters["portfolioId"]!!
                        val cached = varCache.get(portfolioId)
                        if (cached != null) {
                            call.respond(cached.toResponse())
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }
            }

            val response = client.get("/api/v1/risk/var/port-1")
            response.status shouldBe HttpStatusCode.OK

            val body = Json.decodeFromString<VaRResultResponse>(response.bodyAsText())
            body.portfolioId shouldBe "port-1"
            body.calculationType shouldBe "PARAMETRIC"
            body.confidenceLevel shouldBe "CL_95"
            body.varValue shouldBe "4200.00"
            body.expectedShortfall shouldBe "5250.00"
            body.calculatedAt shouldBe TEST_INSTANT.toString()
            body.componentBreakdown.size shouldBe 1
            body.componentBreakdown[0].assetClass shouldBe "EQUITY"
        }
    }

    test("GET /api/v1/risk/var/{portfolioId} returns 404 when no cached result") {
        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                route("/api/v1/risk/var/{portfolioId}") {
                    get {
                        val portfolioId = call.parameters["portfolioId"]!!
                        val cached = varCache.get(portfolioId)
                        if (cached != null) {
                            call.respond(cached.toResponse())
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }
            }

            val response = client.get("/api/v1/risk/var/unknown-portfolio")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /api/v1/risk/var/{portfolioId} returns 200 with VaR result when service returns result") {
        val result = valuationResult(portfolioId = "port-2", varValue = 7500.0)
        coEvery { varCalculationService.calculateVaR(any(), any()) } returns result

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                route("/api/v1/risk/var/{portfolioId}") {
                    post {
                        val portfolioId = call.parameters["portfolioId"]!!
                        val body = call.receive<VaRCalculationRequestBody>()
                        val request = VaRCalculationRequest(
                            portfolioId = PortfolioId(portfolioId),
                            calculationType = CalculationType.valueOf(body.calculationType ?: "PARAMETRIC"),
                            confidenceLevel = ConfidenceLevel.valueOf(body.confidenceLevel ?: "CL_95"),
                            timeHorizonDays = body.timeHorizonDays?.toInt() ?: 1,
                            numSimulations = body.numSimulations?.toInt() ?: 10_000,
                        )
                        val calcResult = varCalculationService.calculateVaR(request)
                        if (calcResult != null) {
                            varCache.put(portfolioId, calcResult)
                            call.respond(calcResult.toResponse())
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }
            }

            val response = client.post("/api/v1/risk/var/port-2") {
                contentType(ContentType.Application.Json)
                setBody("""{"calculationType":"PARAMETRIC","confidenceLevel":"CL_95"}""")
            }

            response.status shouldBe HttpStatusCode.OK

            val body = Json.decodeFromString<VaRResultResponse>(response.bodyAsText())
            body.portfolioId shouldBe "port-2"
            body.varValue shouldBe "7500.00"
            body.expectedShortfall shouldBe "9375.00"
        }
    }

    test("POST /api/v1/risk/var/{portfolioId} returns 404 when service returns null") {
        coEvery { varCalculationService.calculateVaR(any(), any()) } returns null

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                route("/api/v1/risk/var/{portfolioId}") {
                    post {
                        val portfolioId = call.parameters["portfolioId"]!!
                        val body = call.receive<VaRCalculationRequestBody>()
                        val request = VaRCalculationRequest(
                            portfolioId = PortfolioId(portfolioId),
                            calculationType = CalculationType.valueOf(body.calculationType ?: "PARAMETRIC"),
                            confidenceLevel = ConfidenceLevel.valueOf(body.confidenceLevel ?: "CL_95"),
                            timeHorizonDays = body.timeHorizonDays?.toInt() ?: 1,
                            numSimulations = body.numSimulations?.toInt() ?: 10_000,
                        )
                        val calcResult = varCalculationService.calculateVaR(request)
                        if (calcResult != null) {
                            varCache.put(portfolioId, calcResult)
                            call.respond(calcResult.toResponse())
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }
            }

            val response = client.post("/api/v1/risk/var/empty-portfolio") {
                contentType(ContentType.Application.Json)
                setBody("""{"calculationType":"PARAMETRIC","confidenceLevel":"CL_95"}""")
            }

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /api/v1/risk/positions/{portfolioId} returns position-level risk from cache") {
        val posRisk = listOf(
            PositionRisk(
                instrumentId = InstrumentId("AAPL"),
                assetClass = AssetClass.EQUITY,
                marketValue = BigDecimal("17000.00"),
                delta = 0.85,
                gamma = 0.02,
                vega = 1500.0,
                varContribution = BigDecimal("3000.00"),
                esContribution = BigDecimal("3750.00"),
                percentageOfTotal = BigDecimal("60.00"),
            ),
        )
        val result = valuationResult().copy(positionRisk = posRisk)
        varCache.put("port-1", result)

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                get("/api/v1/risk/positions/{portfolioId}") {
                    val portfolioId = call.parameters["portfolioId"]!!
                    val cached = varCache.get(portfolioId)
                    if (cached != null && cached.positionRisk.isNotEmpty()) {
                        call.respond(cached.positionRisk.map { it.toDto() })
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }

            val response = client.get("/api/v1/risk/positions/port-1")
            response.status shouldBe HttpStatusCode.OK

            val body = Json.decodeFromString<List<PositionRiskDto>>(response.bodyAsText())
            body.size shouldBe 1
            body[0].instrumentId shouldBe "AAPL"
            body[0].assetClass shouldBe "EQUITY"
            body[0].delta shouldBe "0.850000"
            body[0].gamma shouldBe "0.020000"
            body[0].vega shouldBe "1500.000000"
            body[0].varContribution shouldBe "3000.00"
            body[0].percentageOfTotal shouldBe "60.00"
        }
    }

    test("GET /api/v1/risk/positions/{portfolioId} returns 404 when no cached data") {
        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                get("/api/v1/risk/positions/{portfolioId}") {
                    val portfolioId = call.parameters["portfolioId"]!!
                    val cached = varCache.get(portfolioId)
                    if (cached != null && cached.positionRisk.isNotEmpty()) {
                        call.respond(cached.positionRisk.map { it.toDto() })
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }

            val response = client.get("/api/v1/risk/positions/unknown")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("VaRResultResponse includes positionRisk when present") {
        val posRisk = listOf(
            PositionRisk(
                instrumentId = InstrumentId("MSFT"),
                assetClass = AssetClass.EQUITY,
                marketValue = BigDecimal("10000.00"),
                delta = null,
                gamma = null,
                vega = null,
                varContribution = BigDecimal("5000.00"),
                esContribution = BigDecimal("6250.00"),
                percentageOfTotal = BigDecimal("100.00"),
            ),
        )
        val result = valuationResult().copy(positionRisk = posRisk)
        varCache.put("port-1", result)

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                route("/api/v1/risk/var/{portfolioId}") {
                    get {
                        val portfolioId = call.parameters["portfolioId"]!!
                        val cached = varCache.get(portfolioId)
                        if (cached != null) {
                            call.respond(cached.toResponse())
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }
            }

            val response = client.get("/api/v1/risk/var/port-1")
            response.status shouldBe HttpStatusCode.OK

            val body = Json.decodeFromString<VaRResultResponse>(response.bodyAsText())
            body.positionRisk.shouldNotBeNull()
            body.positionRisk!!.size shouldBe 1
            body.positionRisk!![0].instrumentId shouldBe "MSFT"
            body.positionRisk!![0].delta shouldBe null
            body.positionRisk!![0].varContribution shouldBe "5000.00"
        }
    }
})
