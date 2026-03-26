package com.kinetix.risk.routes

import com.kinetix.risk.routes.dtos.SaCcrResponse
import com.kinetix.risk.service.SaCcrService
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.saCcrRoutes(service: SaCcrService) {

    route("/api/v1/counterparty/{counterpartyId}") {

        get("/sa-ccr", {
            summary = "Compute SA-CCR (BCBS 279) regulatory EAD for a counterparty"
            description = """
                Returns the Standardised Approach for Counterparty Credit Risk (BCBS 279)
                Exposure at Default for the given counterparty's netting set.

                SA-CCR is the REGULATORY capital model — deterministic and formulaic.
                It coexists with but is distinct from the Monte Carlo PFE model
                (available at /api/v1/counterparty-risk/{counterpartyId}/pfe).
            """.trimIndent()
            tags = listOf("SA-CCR", "Counterparty Risk")
            request {
                pathParameter<String>("counterpartyId") { description = "Counterparty ID" }
                queryParameter<Double>("collateral") {
                    description = "Net collateral held after haircuts (default 0)"
                    required = false
                }
            }
            response {
                code(HttpStatusCode.OK) { body<SaCcrResponse>() }
                code(HttpStatusCode.NotFound) {}
            }
        }) {
            val counterpartyId = call.requirePathParam("counterpartyId")
            val collateralNet = call.request.queryParameters["collateral"]?.toDoubleOrNull() ?: 0.0

            val result = runCatching {
                service.calculateSaCcr(
                    counterpartyId = counterpartyId,
                    collateralNet = collateralNet,
                )
            }.getOrElse { ex ->
                if (ex is IllegalArgumentException) {
                    return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to (ex.message ?: "Not found")))
                }
                return@get call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (ex.message ?: "SA-CCR calculation failed")),
                )
            }

            call.respond(
                SaCcrResponse(
                    nettingSetId = result.nettingSetId,
                    counterpartyId = result.counterpartyId,
                    replacementCost = result.replacementCost,
                    pfeAddon = result.pfeAddon,
                    multiplier = result.multiplier,
                    ead = result.ead,
                    alpha = result.alpha,
                )
            )
        }
    }
}
