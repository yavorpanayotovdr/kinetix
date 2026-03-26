package com.kinetix.position.routes

import com.kinetix.common.model.BookId
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

@Serializable
data class CounterpartyTradeResponse(
    val tradeId: String,
    val instrumentId: String,
    val assetClass: String,
    val side: String,
    val quantity: String,
    val priceAmount: String,
    val priceCurrency: String,
    val instrumentType: String?,
    val counterpartyId: String?,
)

fun Route.counterpartyRoutes(counterpartyExposureService: CounterpartyExposureService) {
    get("/api/v1/counterparties/{counterpartyId}/trades", {
        summary = "Get trades for a counterparty (for SA-CCR position assembly)"
        tags = listOf("Counterparty Risk")
        request {
            pathParameter<String>("counterpartyId") { description = "Counterparty identifier" }
        }
        response {
            code(HttpStatusCode.OK) { body<List<CounterpartyTradeResponse>>() }
        }
    }) {
        val counterpartyId = call.parameters["counterpartyId"]
            ?: throw IllegalArgumentException("Missing path parameter: counterpartyId")
        val trades = counterpartyExposureService.getTradesByCounterparty(counterpartyId)
        call.respond(trades.map {
            CounterpartyTradeResponse(
                tradeId = it.tradeId.value,
                instrumentId = it.instrumentId.value,
                assetClass = it.assetClass.name,
                side = it.side.name,
                quantity = it.quantity.toPlainString(),
                priceAmount = it.price.amount.toPlainString(),
                priceCurrency = it.price.currency.currencyCode,
                instrumentType = it.instrumentType,
                counterpartyId = it.counterpartyId,
            )
        })
    }

    get("/api/v1/counterparty-exposure", {
        summary = "Get counterparty exposure aggregation"
        tags = listOf("Counterparty Risk")
        request {
            queryParameter<String>("bookId") {
                description = "Book identifier"
                required = true
            }
        }
        response {
            code(HttpStatusCode.OK) { body<List<CounterpartyExposureResponse>>() }
        }
    }) {
        val bookId = call.request.queryParameters["bookId"]
            ?: throw IllegalArgumentException("Missing required query parameter: bookId")
        val exposures = counterpartyExposureService.getExposures(BookId(bookId))
        call.respond(exposures.map { it.toResponse() })
    }

    get("/api/v1/counterparties/{counterpartyId}/instrument-netting-sets", {
        summary = "Get instrumentId -> nettingSetId mapping for a counterparty"
        tags = listOf("Counterparty Risk")
        request {
            pathParameter<String>("counterpartyId") { description = "Counterparty identifier" }
        }
        response {
            code(HttpStatusCode.OK) { body<Map<String, String>>() }
        }
    }) {
        val counterpartyId = call.parameters["counterpartyId"]
            ?: throw IllegalArgumentException("Missing path parameter: counterpartyId")
        val mapping = counterpartyExposureService.getInstrumentNettingSets(counterpartyId)
        call.respond(mapping)
    }
}
