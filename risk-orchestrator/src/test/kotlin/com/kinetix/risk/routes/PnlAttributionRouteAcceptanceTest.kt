package com.kinetix.risk.routes

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.BookId
import com.kinetix.risk.model.AttributionDataQuality
import com.kinetix.risk.model.PnlAttribution
import com.kinetix.risk.model.PositionPnlAttribution
import com.kinetix.risk.persistence.PnlAttributionRepository
import com.kinetix.risk.routes.dtos.PnlAttributionResponse
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

private val PORTFOLIO = BookId("port-1")
private val TODAY = LocalDate.of(2025, 1, 15)

private fun bd(value: String) = BigDecimal(value)

private fun sampleAttribution(
    bookId: BookId = PORTFOLIO,
    date: LocalDate = TODAY,
) = PnlAttribution(
    bookId = bookId,
    date = date,
    currency = "USD",
    totalPnl = bd("10.00"),
    deltaPnl = bd("3.00"),
    gammaPnl = bd("1.50"),
    vegaPnl = bd("2.00"),
    thetaPnl = bd("-0.50"),
    rhoPnl = bd("0.30"),
    vannaPnl = bd("0.10"),
    volgaPnl = bd("0.04"),
    charmPnl = bd("-0.002"),
    crossGammaPnl = bd("0.00"),
    unexplainedPnl = bd("3.562"),
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
            vannaPnl = bd("0.07"),
            volgaPnl = bd("0.025"),
            charmPnl = bd("-0.001"),
            crossGammaPnl = bd("0.00"),
            unexplainedPnl = bd("2.506"),
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
            vannaPnl = bd("0.03"),
            volgaPnl = bd("0.015"),
            charmPnl = bd("-0.001"),
            crossGammaPnl = bd("0.00"),
            unexplainedPnl = bd("1.056"),
        ),
    ),
    dataQualityFlag = AttributionDataQuality.FULL_ATTRIBUTION,
    calculatedAt = Instant.parse("2025-01-15T10:00:00Z"),
)

class PnlAttributionRouteAcceptanceTest : FunSpec({

    val pnlAttributionRepository = mockk<PnlAttributionRepository>()

    beforeEach {
        clearMocks(pnlAttributionRepository)
    }

    test("GET /api/v1/risk/pnl-attribution/{bookId} returns latest attribution with cross-Greek fields") {
        val attribution = sampleAttribution()
        coEvery { pnlAttributionRepository.findLatestByBookId(PORTFOLIO) } returns attribution

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                get("/api/v1/risk/pnl-attribution/{bookId}") {
                    val bookId = call.parameters["bookId"]!!
                    val result = pnlAttributionRepository.findLatestByBookId(BookId(bookId))
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
            body.bookId shouldBe "port-1"
            body.date shouldBe "2025-01-15"
            body.totalPnl shouldBe "10.00"
            body.deltaPnl shouldBe "3.00"
            body.gammaPnl shouldBe "1.50"
            body.vegaPnl shouldBe "2.00"
            body.thetaPnl shouldBe "-0.50"
            body.rhoPnl shouldBe "0.30"
            body.vannaPnl shouldBe "0.10"
            body.volgaPnl shouldBe "0.04"
            body.charmPnl shouldBe "-0.002"
            body.crossGammaPnl shouldBe "0.00"
            body.unexplainedPnl shouldBe "3.562"
            body.dataQualityFlag shouldBe "FULL_ATTRIBUTION"
            body.calculatedAt shouldBe "2025-01-15T10:00:00Z"
        }
    }

    test("GET /api/v1/risk/pnl-attribution/{bookId} returns position attributions with cross-Greek fields") {
        val attribution = sampleAttribution()
        coEvery { pnlAttributionRepository.findLatestByBookId(PORTFOLIO) } returns attribution

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                get("/api/v1/risk/pnl-attribution/{bookId}") {
                    val bookId = call.parameters["bookId"]!!
                    val result = pnlAttributionRepository.findLatestByBookId(BookId(bookId))
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
            aapl.vannaPnl shouldBe "0.07"
            aapl.volgaPnl shouldBe "0.025"
            aapl.charmPnl shouldBe "-0.001"
            aapl.crossGammaPnl shouldBe "0.00"
            aapl.unexplainedPnl shouldBe "2.506"

            val msft = body.positionAttributions[1]
            msft.instrumentId shouldBe "MSFT"
            msft.totalPnl shouldBe "3.00"
            msft.vannaPnl shouldBe "0.03"
        }
    }

    test("GET /api/v1/risk/pnl-attribution/{bookId} with date query parameter") {
        val attribution = sampleAttribution()
        coEvery { pnlAttributionRepository.findByBookIdAndDate(PORTFOLIO, TODAY) } returns attribution

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                get("/api/v1/risk/pnl-attribution/{bookId}") {
                    val bookId = call.parameters["bookId"]!!
                    val dateParam = call.request.queryParameters["date"]
                    val result = if (dateParam != null) {
                        val date = java.time.LocalDate.parse(dateParam)
                        pnlAttributionRepository.findByBookIdAndDate(BookId(bookId), date)
                    } else {
                        pnlAttributionRepository.findLatestByBookId(BookId(bookId))
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
            body.dataQualityFlag shouldBe "FULL_ATTRIBUTION"
        }
    }

    test("GET /api/v1/risk/pnl-attribution/{bookId} returns 404 when no attribution exists") {
        coEvery { pnlAttributionRepository.findLatestByBookId(any()) } returns null

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                get("/api/v1/risk/pnl-attribution/{bookId}") {
                    val bookId = call.parameters["bookId"]!!
                    val result = pnlAttributionRepository.findLatestByBookId(BookId(bookId))
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
