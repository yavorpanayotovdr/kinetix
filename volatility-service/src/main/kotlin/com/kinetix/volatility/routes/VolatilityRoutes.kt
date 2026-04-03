package com.kinetix.volatility.routes

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.VolPoint
import com.kinetix.common.model.VolSurface
import com.kinetix.common.model.VolatilitySource
import com.kinetix.volatility.persistence.VolSurfaceRepository
import com.kinetix.volatility.routes.dtos.IngestVolSurfaceRequest
import com.kinetix.volatility.routes.dtos.VolPointDiffDto
import com.kinetix.volatility.routes.dtos.VolPointDto
import com.kinetix.volatility.routes.dtos.VolSurfaceDiffResponse
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
            route("/latest") {
                get({
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
            }

            route("/diff") {
                get({
                    summary = "Get vol surface diff vs a comparison date"
                    tags = listOf("Volatility")
                    request {
                        pathParameter<String>("instrumentId") { description = "Instrument identifier" }
                        queryParameter<String>("compareDate") { description = "Comparison date (ISO-8601 instant)" }
                    }
                }) {
                    val instrumentId = InstrumentId(call.requirePathParam("instrumentId"))
                    val compareDateStr = call.request.queryParameters["compareDate"]
                        ?: throw IllegalArgumentException("Missing required query parameter: compareDate")
                    val compareDate = try {
                        Instant.parse(compareDateStr)
                    } catch (_: Exception) {
                        throw IllegalArgumentException("Invalid compareDate — expected ISO-8601 instant: $compareDateStr")
                    }

                    val baseSurface = volSurfaceRepository.findLatest(instrumentId)
                    if (baseSurface == null) {
                        call.respond(HttpStatusCode.NotFound); return@get
                    }
                    val compareSurface = volSurfaceRepository.findAtOrBefore(instrumentId, compareDate)
                    if (compareSurface == null) {
                        call.respond(HttpStatusCode.NotFound); return@get
                    }

                    val diffs = computeUnionGridDiff(baseSurface.points, compareSurface.points)

                    call.respond(
                        VolSurfaceDiffResponse(
                            instrumentId = instrumentId.value,
                            baseDate = baseSurface.asOf.toString(),
                            compareDate = compareSurface.asOf.toString(),
                            diffs = diffs,
                        )
                    )
                }
            }

            route("/history") {
                get({
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
        }

        route("/surfaces") {
            post({
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
}

private fun VolSurface.toResponse() = VolSurfaceResponse(
    instrumentId = instrumentId.value,
    asOfDate = asOf.toString(),
    points = points.map { VolPointDto(it.strike.toDouble(), it.maturityDays, it.impliedVol.toDouble()) },
    source = source.name,
    lastUpdatedAt = asOf.toString(),
)

/**
 * Computes vol differences over the union of both surfaces' (strike, maturity) grids.
 *
 * When a grid point exists in one surface but not the other, the missing vol is filled
 * by nearest-neighbour lookup: the point with the same maturityDays whose strike is
 * closest (in absolute terms) to the target strike. This ensures no points are silently
 * dropped when the two surfaces were quoted on different strike ladders.
 */
private fun computeUnionGridDiff(
    basePoints: List<VolPoint>,
    comparePoints: List<VolPoint>,
): List<VolPointDiffDto> {
    val baseMap = basePoints.associateBy { it.strike to it.maturityDays }
    val compareMap = comparePoints.associateBy { it.strike to it.maturityDays }

    // Group each surface's points by maturity so nearest-neighbour search stays within-maturity.
    val baseByMaturity = basePoints.groupBy { it.maturityDays }
    val compareByMaturity = comparePoints.groupBy { it.maturityDays }

    // Union of all (strike, maturity) grid coordinates from both surfaces.
    val unionGrid: Set<Pair<BigDecimal, Int>> = baseMap.keys + compareMap.keys

    return unionGrid.map { (strike, maturityDays) ->
        val baseVol = baseMap[strike to maturityDays]?.impliedVol
            ?: nearestVol(strike, maturityDays, baseByMaturity)
        val compareVol = compareMap[strike to maturityDays]?.impliedVol
            ?: nearestVol(strike, maturityDays, compareByMaturity)

        VolPointDiffDto(
            strike = strike.toDouble(),
            maturityDays = maturityDays,
            baseVol = baseVol.toDouble(),
            compareVol = compareVol.toDouble(),
            diff = baseVol.toDouble() - compareVol.toDouble(),
        )
    }.sortedWith(compareBy({ it.maturityDays }, { it.strike }))
}

/**
 * Returns the implied vol of the point in [pointsByMaturity] whose strike is nearest
 * to [targetStrike] among all points with the given [maturityDays].
 */
private fun nearestVol(
    targetStrike: BigDecimal,
    maturityDays: Int,
    pointsByMaturity: Map<Int, List<VolPoint>>,
): BigDecimal {
    val candidates = pointsByMaturity[maturityDays] ?: emptyList()
    return candidates.minByOrNull { (it.strike - targetStrike).abs() }?.impliedVol
        ?: BigDecimal.ZERO
}
