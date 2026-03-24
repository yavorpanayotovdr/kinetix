package com.kinetix.risk.routes

import com.kinetix.common.model.BookId
import com.kinetix.risk.model.GreekImpact
import com.kinetix.risk.model.HedgeConstraints
import com.kinetix.risk.model.HedgeRecommendation
import com.kinetix.risk.model.HedgeSuggestion
import com.kinetix.risk.model.HedgeTarget
import com.kinetix.risk.routes.dtos.GreekImpactDto
import com.kinetix.risk.routes.dtos.HedgeRecommendationResponse
import com.kinetix.risk.routes.dtos.HedgeSuggestRequestBody
import com.kinetix.risk.routes.dtos.HedgeSuggestionDto
import com.kinetix.risk.service.HedgeRecommendationService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.hedgeRecommendationRoutes(service: HedgeRecommendationService) {

    post("/api/v1/risk/hedge-suggest/{bookId}") {
        val bookId = BookId(call.requirePathParam("bookId"))
        val body = call.receive<HedgeSuggestRequestBody>()

        val target = try {
            HedgeTarget.valueOf(body.targetMetric.uppercase())
        } catch (e: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "invalid_target", "message" to "Unknown target metric: ${body.targetMetric}. Valid values: DELTA, GAMMA, VEGA, VAR"),
            )
            return@post
        }

        val constraints = HedgeConstraints(
            maxNotional = body.maxNotional,
            maxSuggestions = body.maxSuggestions,
            respectPositionLimits = body.respectPositionLimits,
            instrumentUniverse = body.instrumentUniverse,
            allowedSides = body.allowedSides,
        )

        val recommendation = try {
            service.suggestHedge(bookId, target, body.targetReductionPct, constraints)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_request", "message" to (e.message ?: "Bad request")))
            return@post
        } catch (e: IllegalStateException) {
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "service_error", "message" to (e.message ?: "Internal error")))
            return@post
        }
        call.respond(HttpStatusCode.Created, recommendation.toResponse())
    }

    get("/api/v1/risk/hedge-suggest/{bookId}") {
        val bookId = BookId(call.requirePathParam("bookId"))
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
        val recommendations = service.getLatestRecommendations(bookId, limit)
        call.respond(recommendations.map { it.toResponse() })
    }

    get("/api/v1/risk/hedge-suggest/{bookId}/{id}") {
        val id = try {
            UUID.fromString(call.requirePathParam("id"))
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_id", "message" to "Invalid UUID"))
            return@get
        }

        val recommendation = service.getRecommendation(id)
        if (recommendation == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(recommendation.toResponse())
        }
    }
}

private fun HedgeRecommendation.toResponse() = HedgeRecommendationResponse(
    id = id.toString(),
    bookId = bookId,
    targetMetric = targetMetric.name,
    targetReductionPct = targetReductionPct,
    requestedAt = requestedAt.toString(),
    status = status.name,
    expiresAt = expiresAt.toString(),
    acceptedBy = acceptedBy,
    acceptedAt = acceptedAt?.toString(),
    sourceJobId = sourceJobId,
    suggestions = suggestions.map { it.toDto() },
    preHedgeGreeks = preHedgeGreeks.toDto(),
    totalEstimatedCost = totalEstimatedCost,
    isExpired = isExpired,
)

private fun HedgeSuggestion.toDto() = HedgeSuggestionDto(
    instrumentId = instrumentId,
    instrumentType = instrumentType,
    side = side,
    quantity = quantity,
    estimatedCost = estimatedCost,
    crossingCost = crossingCost,
    carrycostPerDay = carrycostPerDay,
    targetReduction = targetReduction,
    targetReductionPct = targetReductionPct,
    residualMetric = residualMetric,
    greekImpact = greekImpact.toDto(),
    liquidityTier = liquidityTier,
    dataQuality = dataQuality,
)

private fun GreekImpact.toDto() = GreekImpactDto(
    deltaBefore = deltaBefore,
    deltaAfter = deltaAfter,
    gammaBefore = gammaBefore,
    gammaAfter = gammaAfter,
    vegaBefore = vegaBefore,
    vegaAfter = vegaAfter,
    thetaBefore = thetaBefore,
    thetaAfter = thetaAfter,
    rhoBefore = rhoBefore,
    rhoAfter = rhoAfter,
)
