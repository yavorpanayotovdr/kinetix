package com.kinetix.price.routes

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PricePoint
import com.kinetix.common.model.Money
import com.kinetix.price.persistence.PriceRepository
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.time.Instant

// --- Response DTOs matching gateway's expected shapes ---

@Serializable
data class MoneyDto(val amount: String, val currency: String)

@Serializable
data class PricePointResponse(
    val instrumentId: String,
    val price: MoneyDto,
    val timestamp: String,
    val source: String,
)

@Serializable
data class ErrorResponse(val error: String, val message: String)

private fun Money.toDto() = MoneyDto(amount.toPlainString(), currency.currencyCode)

private fun PricePoint.toResponse() = PricePointResponse(
    instrumentId = instrumentId.value,
    price = price.toDto(),
    timestamp = timestamp.toString(),
    source = source.name,
)

// --- Routes ---

fun Route.priceRoutes(repository: PriceRepository) {
    route("/api/v1/prices/{instrumentId}") {

        get("/latest") {
            val instrumentId = InstrumentId(call.requirePathParam("instrumentId"))
            val point = repository.findLatest(instrumentId)
            if (point != null) {
                call.respond(point.toResponse())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        get("/history") {
            val instrumentId = InstrumentId(call.requirePathParam("instrumentId"))
            val from = call.queryParameters["from"]
                ?: throw IllegalArgumentException("Missing required query parameter: from")
            val to = call.queryParameters["to"]
                ?: throw IllegalArgumentException("Missing required query parameter: to")
            val points = repository.findByInstrumentId(instrumentId, Instant.parse(from), Instant.parse(to))
            call.respond(points.map { it.toResponse() })
        }
    }
}
