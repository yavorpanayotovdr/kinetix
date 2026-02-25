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
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import java.math.BigDecimal
import java.time.Instant

fun Route.volatilityRoutes(
    volSurfaceRepository: VolSurfaceRepository,
    ingestionService: VolatilityIngestionService,
) {
    route("/api/v1/volatility") {
        route("/{instrumentId}/surface") {
            get("/latest", {
                summary = "Get latest volatility surface"
                tags = listOf("Volatility")
                request {
                    pathParameter<String>("instrumentId") { description = "Instrument identifier" }
                }
            }) {
                val instrumentId = InstrumentId(call.requirePathParam("instrumentId"))
                val surface = volSurfaceRepository.findLatest(instrumentId)
                if (surface != null) {
                    call.respond(surface.toResponse())
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            get("/history", {
                summary = "Get volatility surface history"
                tags = listOf("Volatility")
                request {
                    pathParameter<String>("instrumentId") { description = "Instrument identifier" }
                    queryParameter<String>("from") { description = "Start of time range (ISO-8601)" }
                    queryParameter<String>("to") { description = "End of time range (ISO-8601)" }
                }
            }) {
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

        post("/surfaces", {
            summary = "Ingest a volatility surface"
            tags = listOf("Volatility")
            request {
                body<IngestVolSurfaceRequest>()
            }
        }) {
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
