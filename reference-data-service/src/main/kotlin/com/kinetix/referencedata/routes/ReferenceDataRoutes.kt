package com.kinetix.referencedata.routes

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import com.kinetix.referencedata.persistence.CreditSpreadRepository
import com.kinetix.referencedata.persistence.DividendYieldRepository
import com.kinetix.referencedata.routes.dtos.CreditSpreadResponse
import com.kinetix.referencedata.routes.dtos.DividendYieldResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.referenceDataRoutes(
    dividendYieldRepository: DividendYieldRepository,
    creditSpreadRepository: CreditSpreadRepository,
) {
    route("/api/v1/reference-data") {
        route("/dividends/{instrumentId}") {
            get("/latest") {
                val instrumentId = InstrumentId(call.requirePathParam("instrumentId"))
                val dividendYield = dividendYieldRepository.findLatest(instrumentId)
                if (dividendYield != null) {
                    call.respond(dividendYield.toResponse())
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        route("/credit-spreads/{instrumentId}") {
            get("/latest") {
                val instrumentId = InstrumentId(call.requirePathParam("instrumentId"))
                val creditSpread = creditSpreadRepository.findLatest(instrumentId)
                if (creditSpread != null) {
                    call.respond(creditSpread.toResponse())
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
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
