package com.kinetix.volatility.routes

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.VolPoint
import com.kinetix.common.model.VolSurface
import com.kinetix.common.model.VolatilitySource
import com.kinetix.volatility.persistence.VolSurfaceRepository
import com.kinetix.volatility.routes.dtos.IngestVolSurfaceRequest
import com.kinetix.volatility.routes.dtos.VolPointDto
import com.kinetix.volatility.routes.dtos.VolSurfaceResponse
import com.kinetix.volatility.service.VolatilityIngestionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.math.BigDecimal
import java.time.Instant

fun Route.volatilityRoutes(
    volSurfaceRepository: VolSurfaceRepository,
    ingestionService: VolatilityIngestionService,
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

        post("/surfaces") {
            val request = call.receive<IngestVolSurfaceRequest>()
            val surface = VolSurface(
                instrumentId = InstrumentId(request.instrumentId),
                asOf = Instant.now(),
                points = request.points.map {
                    VolPoint(BigDecimal(it.strike.toString()), it.maturityDays, BigDecimal(it.impliedVol.toString()))
                },
                source = VolatilitySource.valueOf(request.source),
            )
            ingestionService.ingest(surface)
            call.respond(HttpStatusCode.Created, surface.toResponse())
        }
    }
}

private fun VolSurface.toResponse() = VolSurfaceResponse(
    instrumentId = instrumentId.value,
    asOfDate = asOf.toString(),
    points = points.map { VolPointDto(it.strike.toDouble(), it.maturityDays, it.impliedVol.toDouble()) },
    source = source.name,
)
