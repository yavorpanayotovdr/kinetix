package com.kinetix.referencedata.routes

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.ReferenceDataSource
import com.kinetix.referencedata.persistence.CreditSpreadRepository
import com.kinetix.referencedata.persistence.DividendYieldRepository
import com.kinetix.referencedata.routes.dtos.CreditSpreadResponse
import com.kinetix.referencedata.routes.dtos.DividendYieldResponse
import com.kinetix.referencedata.routes.dtos.IngestCreditSpreadRequest
import com.kinetix.referencedata.routes.dtos.IngestDividendYieldRequest
import com.kinetix.referencedata.service.ReferenceDataIngestionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import java.time.Instant
import java.time.LocalDate

fun Route.referenceDataRoutes(
    dividendYieldRepository: DividendYieldRepository,
    creditSpreadRepository: CreditSpreadRepository,
    ingestionService: ReferenceDataIngestionService,
) {
    route("/api/v1/reference-data") {
        route("/dividends/{instrumentId}") {
            get("/latest", {
                summary = "Get latest dividend yield"
                tags = listOf("Dividends")
                request {
                    pathParameter<String>("instrumentId") { description = "Instrument identifier" }
                }
            }) {
                val instrumentId = InstrumentId(call.requirePathParam("instrumentId"))
                val dividendYield = dividendYieldRepository.findLatest(instrumentId)
                if (dividendYield != null) {
                    call.respond(dividendYield.toResponse())
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        post("/dividends", {
            summary = "Ingest a dividend yield"
            tags = listOf("Dividends")
            request {
                body<IngestDividendYieldRequest>()
            }
        }) {
            val request = call.receive<IngestDividendYieldRequest>()
            val dividendYield = DividendYield(
                instrumentId = InstrumentId(request.instrumentId),
                yield = request.yield,
                exDate = request.exDate?.let { LocalDate.parse(it) },
                asOfDate = Instant.now(),
                source = ReferenceDataSource.valueOf(request.source),
            )
            ingestionService.ingest(dividendYield)
            call.respond(HttpStatusCode.Created, dividendYield.toResponse())
        }

        route("/credit-spreads/{instrumentId}") {
            get("/latest", {
                summary = "Get latest credit spread"
                tags = listOf("Credit Spreads")
                request {
                    pathParameter<String>("instrumentId") { description = "Instrument identifier" }
                }
            }) {
                val instrumentId = InstrumentId(call.requirePathParam("instrumentId"))
                val creditSpread = creditSpreadRepository.findLatest(instrumentId)
                if (creditSpread != null) {
                    call.respond(creditSpread.toResponse())
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        post("/credit-spreads", {
            summary = "Ingest a credit spread"
            tags = listOf("Credit Spreads")
            request {
                body<IngestCreditSpreadRequest>()
            }
        }) {
            val request = call.receive<IngestCreditSpreadRequest>()
            val creditSpread = CreditSpread(
                instrumentId = InstrumentId(request.instrumentId),
                spread = request.spread,
                rating = request.rating,
                asOfDate = Instant.now(),
                source = ReferenceDataSource.valueOf(request.source),
            )
            ingestionService.ingest(creditSpread)
            call.respond(HttpStatusCode.Created, creditSpread.toResponse())
        }
    }
}

private fun DividendYield.toResponse() = DividendYieldResponse(
    instrumentId = instrumentId.value,
    yield = yield,
    exDate = exDate?.toString(),
    asOfDate = asOfDate.toString(),
    source = source.name,
)

private fun CreditSpread.toResponse() = CreditSpreadResponse(
    instrumentId = instrumentId.value,
    spread = spread,
    rating = rating,
    asOfDate = asOfDate.toString(),
    source = source.name,
)
