package com.kinetix.risk.routes

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.PnlAttribution
import com.kinetix.risk.model.PositionPnlAttribution
import com.kinetix.risk.persistence.PnlAttributionRepository
import com.kinetix.risk.routes.dtos.PnlAttributionResponse
import com.kinetix.risk.routes.dtos.PositionPnlAttributionDto
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
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
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private val PORTFOLIO = PortfolioId("port-1")
private val TODAY = LocalDate.of(2025, 1, 15)

private fun bd(value: String) = BigDecimal(value)

private fun sampleAttribution(
    portfolioId: PortfolioId = PORTFOLIO,
    date: LocalDate = TODAY,
) = PnlAttribution(
    portfolioId = portfolioId,
    date = date,
    totalPnl = bd("10.00"),
    deltaPnl = bd("3.00"),
    gammaPnl = bd("1.50"),
    vegaPnl = bd("2.00"),
    thetaPnl = bd("-0.50"),
    rhoPnl = bd("0.30"),
    unexplainedPnl = bd("3.70"),
    positionAttributions = listOf(
        PositionPnlAttribution(
            instrumentId = InstrumentId("AAPL"),
            assetClass = AssetClass.EQUITY,
            totalPnl = bd("7.00"),
            deltaPnl = bd("2.00"),
            gammaPnl = bd("1.00"),
            vegaPnl = bd("1.50"),
            thetaPnl = bd("-0.30"),
            rhoPnl = bd("0.20"),
            unexplainedPnl = bd("2.60"),
        ),
        PositionPnlAttribution(
            instrumentId = InstrumentId("MSFT"),
            assetClass = AssetClass.EQUITY,
            totalPnl = bd("3.00"),
            deltaPnl = bd("1.00"),
            gammaPnl = bd("0.50"),
            vegaPnl = bd("0.50"),
            thetaPnl = bd("-0.20"),
            rhoPnl = bd("0.10"),
            unexplainedPnl = bd("1.10"),
        ),
    ),
    calculatedAt = Instant.parse("2025-01-15T10:00:00Z"),
)

class PnlAttributionRouteAcceptanceTest : FunSpec({

    val pnlAttributionRepository = mockk<PnlAttributionRepository>()

    beforeEach {
        clearMocks(pnlAttributionRepository)
    }

    test("GET /api/v1/risk/pnl-attribution/{portfolioId} returns latest attribution") {
        val attribution = sampleAttribution()
        coEvery { pnlAttributionRepository.findLatestByPortfolioId(PORTFOLIO) } returns attribution

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                get("/api/v1/risk/pnl-attribution/{portfolioId}") {
                    val portfolioId = call.parameters["portfolioId"]!!
                    val result = pnlAttributionRepository.findLatestByPortfolioId(PortfolioId(portfolioId))
                    if (result != null) {
                        call.respond(result.toResponse())
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }

            val response = client.get("/api/v1/risk/pnl-attribution/port-1")
            response.status shouldBe HttpStatusCode.OK

            val body = Json.decodeFromString<PnlAttributionResponse>(response.bodyAsText())
            body.portfolioId shouldBe "port-1"
            body.date shouldBe "2025-01-15"
            body.totalPnl shouldBe "10.00"
            body.deltaPnl shouldBe "3.00"
            body.gammaPnl shouldBe "1.50"
            body.vegaPnl shouldBe "2.00"
            body.thetaPnl shouldBe "-0.50"
            body.rhoPnl shouldBe "0.30"
            body.unexplainedPnl shouldBe "3.70"
            body.calculatedAt shouldBe "2025-01-15T10:00:00Z"
        }
    }

    test("GET /api/v1/risk/pnl-attribution/{portfolioId} returns position attributions in correct structure") {
        val attribution = sampleAttribution()
        coEvery { pnlAttributionRepository.findLatestByPortfolioId(PORTFOLIO) } returns attribution

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                get("/api/v1/risk/pnl-attribution/{portfolioId}") {
                    val portfolioId = call.parameters["portfolioId"]!!
                    val result = pnlAttributionRepository.findLatestByPortfolioId(PortfolioId(portfolioId))
                    if (result != null) {
                        call.respond(result.toResponse())
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }

            val response = client.get("/api/v1/risk/pnl-attribution/port-1")
            response.status shouldBe HttpStatusCode.OK

            val body = Json.decodeFromString<PnlAttributionResponse>(response.bodyAsText())
            body.positionAttributions shouldHaveSize 2

            val aapl = body.positionAttributions[0]
            aapl.instrumentId shouldBe "AAPL"
            aapl.assetClass shouldBe "EQUITY"
            aapl.totalPnl shouldBe "7.00"
            aapl.deltaPnl shouldBe "2.00"
            aapl.gammaPnl shouldBe "1.00"
            aapl.vegaPnl shouldBe "1.50"
            aapl.thetaPnl shouldBe "-0.30"
            aapl.rhoPnl shouldBe "0.20"
            aapl.unexplainedPnl shouldBe "2.60"

            val msft = body.positionAttributions[1]
            msft.instrumentId shouldBe "MSFT"
            msft.totalPnl shouldBe "3.00"
        }
    }

    test("GET /api/v1/risk/pnl-attribution/{portfolioId} with date query parameter") {
        val attribution = sampleAttribution()
        coEvery { pnlAttributionRepository.findByPortfolioIdAndDate(PORTFOLIO, TODAY) } returns attribution

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                get("/api/v1/risk/pnl-attribution/{portfolioId}") {
                    val portfolioId = call.parameters["portfolioId"]!!
                    val dateParam = call.request.queryParameters["date"]
                    val result = if (dateParam != null) {
                        val date = java.time.LocalDate.parse(dateParam)
                        pnlAttributionRepository.findByPortfolioIdAndDate(PortfolioId(portfolioId), date)
                    } else {
                        pnlAttributionRepository.findLatestByPortfolioId(PortfolioId(portfolioId))
                    }
                    if (result != null) {
                        call.respond(result.toResponse())
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }

            val response = client.get("/api/v1/risk/pnl-attribution/port-1?date=2025-01-15")
            response.status shouldBe HttpStatusCode.OK

            val body = Json.decodeFromString<PnlAttributionResponse>(response.bodyAsText())
            body.date shouldBe "2025-01-15"
        }
    }

    test("GET /api/v1/risk/pnl-attribution/{portfolioId} returns 404 when no attribution exists") {
        coEvery { pnlAttributionRepository.findLatestByPortfolioId(any()) } returns null

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                get("/api/v1/risk/pnl-attribution/{portfolioId}") {
                    val portfolioId = call.parameters["portfolioId"]!!
                    val result = pnlAttributionRepository.findLatestByPortfolioId(PortfolioId(portfolioId))
                    if (result != null) {
                        call.respond(result.toResponse())
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }

            val response = client.get("/api/v1/risk/pnl-attribution/unknown-portfolio")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
