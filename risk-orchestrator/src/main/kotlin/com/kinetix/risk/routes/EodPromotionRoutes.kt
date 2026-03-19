package com.kinetix.risk.routes

import com.kinetix.risk.model.EodPromotionException
import com.kinetix.risk.model.RunLabel
import com.kinetix.risk.routes.dtos.EodPromotionResponse
import com.kinetix.risk.routes.dtos.PromoteEodRequest
import com.kinetix.risk.service.EodPromotionService
import com.kinetix.risk.service.IncompleteMarketDataException
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate
import java.util.UUID

fun Route.eodPromotionRoutes(eodPromotionService: EodPromotionService) {

    patch("/api/v1/risk/jobs/{jobId}/label", {
        summary = "Promote or demote a job's EOD label"
        tags = listOf("EOD Promotion")
        request {
            pathParameter<String>("jobId") { description = "Job identifier" }
        }
    }) {
        val jobIdStr = call.requirePathParam("jobId")
        val jobId = try {
            UUID.fromString(jobIdStr)
        } catch (_: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid job ID"))
            return@patch
        }

        val body = call.receive<PromoteEodRequest>()
        val force = call.request.queryParameters["force"]?.toBoolean() ?: false

        if (body.label == RunLabel.OFFICIAL_EOD.name) {
            try {
                val promoted = eodPromotionService.promoteToOfficialEod(jobId, body.promotedBy, force)
                call.respond(
                    HttpStatusCode.OK,
                    EodPromotionResponse(
                        jobId = promoted.jobId.toString(),
                        portfolioId = promoted.portfolioId,
                        valuationDate = promoted.valuationDate.toString(),
                        runLabel = promoted.runLabel?.name ?: RunLabel.ADHOC.name,
                        promotedAt = promoted.promotedAt?.toString(),
                        promotedBy = promoted.promotedBy,
                    )
                )
            } catch (e: EodPromotionException.JobNotFound) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: EodPromotionException.JobNotCompleted) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            } catch (e: EodPromotionException.SelfPromotion) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to e.message))
            } catch (e: EodPromotionException.AlreadyPromoted) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
            } catch (e: EodPromotionException.ConflictingOfficialEod) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to e.message))
            } catch (e: IncompleteMarketDataException) {
                call.respond(HttpStatusCode.UnprocessableEntity, mapOf("error" to e.message))
            }
        } else if (body.label == RunLabel.ADHOC.name) {
            try {
                val demoted = eodPromotionService.demoteFromOfficialEod(jobId, body.promotedBy)
                call.respond(
                    HttpStatusCode.OK,
                    EodPromotionResponse(
                        jobId = demoted.jobId.toString(),
                        portfolioId = demoted.portfolioId,
                        valuationDate = demoted.valuationDate.toString(),
                        runLabel = demoted.runLabel?.name ?: RunLabel.ADHOC.name,
                        promotedAt = demoted.promotedAt?.toString(),
                        promotedBy = demoted.promotedBy,
                    )
                )
            } catch (e: EodPromotionException.JobNotFound) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            } catch (e: EodPromotionException.NotPromoted) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
            }
        } else {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid label. Expected OFFICIAL_EOD or ADHOC."))
        }
    }

    get("/api/v1/risk/jobs/{portfolioId}/official-eod", {
        summary = "Get the Official EOD designation for a portfolio and date"
        tags = listOf("EOD Promotion")
        request {
            pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
            queryParameter<String>("date") {
                description = "Valuation date (YYYY-MM-DD)"
                required = true
            }
        }
    }) {
        val portfolioId = call.requirePathParam("portfolioId")
        val dateStr = call.request.queryParameters["date"]
        if (dateStr.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "date parameter is required"))
            return@get
        }

        val date = try {
            LocalDate.parse(dateStr)
        } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid date format. Expected YYYY-MM-DD."))
            return@get
        }

        val job = eodPromotionService.findOfficialEod(portfolioId, date)
        if (job != null) {
            call.respond(
                HttpStatusCode.OK,
                EodPromotionResponse(
                    jobId = job.jobId.toString(),
                    portfolioId = job.portfolioId,
                    valuationDate = job.valuationDate.toString(),
                    runLabel = job.runLabel?.name ?: RunLabel.ADHOC.name,
                    promotedAt = job.promotedAt?.toString(),
                    promotedBy = job.promotedBy,
                )
            )
        } else {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "No Official EOD designation for $portfolioId on $dateStr"))
        }
    }
}
