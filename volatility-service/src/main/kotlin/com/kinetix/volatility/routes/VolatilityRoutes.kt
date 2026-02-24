package com.kinetix.volatility.routes

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.VolSurface
import com.kinetix.volatility.persistence.VolSurfaceRepository
import com.kinetix.volatility.routes.dtos.VolPointDto
import com.kinetix.volatility.routes.dtos.VolSurfaceResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import java.time.Instant

fun Route.volatilityRoutes(
    volSurfaceRepository: VolSurfaceRepository,
) {
    route("/api/v1/volatility") {
        route("/{instrumentId}/surface") {
            get("/latest") {
                val instrumentId = InstrumentId(call.requirePathParam("instrumentId"))
                val surface = volSurfaceRepository.findLatest(instrumentId)
                if (surface != null) {
                    call.respond(surface.toResponse())
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            get("/history") {
                val instrumentId = InstrumentId(call.requirePathParam("instrumentId"))
                val from = Instant.parse(
                    call.request.queryParameters["from"]
                        ?: throw IllegalArgumentException("Missing required query parameter: from")
                )
                val to = Instant.parse(
                    call.request.queryParameters["to"]
                        ?: throw IllegalArgumentException("Missing required query parameter: to")
                )
                val surfaces = volSurfaceRepository.findByTimeRange(instrumentId, from, to)
                call.respond(surfaces.map { it.toResponse() })
            }
        }
    }
}

private fun VolSurface.toResponse() = VolSurfaceResponse(
    instrumentId = instrumentId.value,
    asOfDate = asOf.toString(),
    points = points.map { VolPointDto(it.strike.toDouble(), it.maturityDays, it.impliedVol.toDouble()) },
    source = source.name,
)
