package com.kinetix.position.routes

import com.kinetix.common.model.PortfolioId
import com.kinetix.position.model.CounterpartyExposure
import com.kinetix.position.service.CounterpartyExposureService
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class CounterpartyExposureResponse(
    val counterpartyId: String,
    val netExposure: String,
    val grossExposure: String,
    val positionCount: Int,
)

private fun CounterpartyExposure.toResponse() = CounterpartyExposureResponse(
    counterpartyId = counterpartyId,
    netExposure = netExposure.toPlainString(),
    grossExposure = grossExposure.toPlainString(),
    positionCount = positionCount,
)

fun Route.counterpartyRoutes(counterpartyExposureService: CounterpartyExposureService) {
    get("/api/v1/counterparty-exposure", {
        summary = "Get counterparty exposure aggregation"
        tags = listOf("Counterparty Risk")
        request {
            queryParameter<String>("portfolioId") {
                description = "Portfolio identifier"
                required = true
            }
        }
        response {
            code(HttpStatusCode.OK) { body<List<CounterpartyExposureResponse>>() }
        }
    }) {
        val portfolioId = call.request.queryParameters["portfolioId"]
            ?: throw IllegalArgumentException("Missing required query parameter: portfolioId")
        val exposures = counterpartyExposureService.getExposures(PortfolioId(portfolioId))
        call.respond(exposures.map { it.toResponse() })
    }
}
