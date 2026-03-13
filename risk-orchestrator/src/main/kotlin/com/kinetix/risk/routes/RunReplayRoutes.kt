package com.kinetix.risk.routes

import com.kinetix.risk.model.ReplayResult
import com.kinetix.risk.model.RunManifest
import com.kinetix.risk.routes.dtos.ErrorResponse
import com.kinetix.risk.routes.dtos.ReplayResponse
import com.kinetix.risk.routes.dtos.RunManifestResponse
import com.kinetix.risk.service.ReplayService
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.runReplayRoutes(replayService: ReplayService) {

    get("/api/v1/risk/runs/{jobId}/manifest", {
        summary = "Get the run manifest for a valuation job"
        tags = listOf("Run Reproducibility")
        request {
            pathParameter<String>("jobId") { description = "Valuation job identifier" }
        }
    }) {
        val jobIdStr = call.requirePathParam("jobId")
        val jobId = try {
            UUID.fromString(jobIdStr)
        } catch (_: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", "Invalid job ID format"))
            return@get
        }

        val manifest = replayService.getManifest(jobId)
        if (manifest != null) {
            call.respond(manifest.toResponse())
        } else {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "No manifest found for job $jobId"))
        }
    }

    post("/api/v1/risk/runs/{jobId}/replay", {
        summary = "Replay a past valuation job using its frozen inputs"
        tags = listOf("Run Reproducibility")
        request {
            pathParameter<String>("jobId") { description = "Valuation job identifier to replay" }
        }
    }) {
        val jobIdStr = call.requirePathParam("jobId")
        val jobId = try {
            UUID.fromString(jobIdStr)
        } catch (_: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", "Invalid job ID format"))
            return@post
        }

        when (val result = replayService.replay(jobId)) {
            is ReplayResult.ManifestNotFound ->
                call.respond(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "No manifest found for job $jobId"))
            is ReplayResult.BlobMissing ->
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorResponse(
                        "BLOB_MISSING",
                        "Market data blob ${result.contentHash} (${result.dataType}/${result.instrumentId}) " +
                            "is no longer available for manifest ${result.manifestId}",
                    ),
                )
            is ReplayResult.Error ->
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("REPLAY_ERROR", result.message))
            is ReplayResult.Success ->
                call.respond(
                    ReplayResponse(
                        manifest = result.manifest.toResponse(),
                        replayVarValue = result.replayResult.varValue,
                        replayExpectedShortfall = result.replayResult.expectedShortfall,
                        replayModelVersion = result.replayResult.modelVersion,
                        inputDigestMatch = result.inputDigestMatch,
                        originalInputDigest = result.originalInputDigest,
                        replayInputDigest = result.replayInputDigest,
                        originalVarValue = result.originalVarValue,
                        originalExpectedShortfall = result.originalExpectedShortfall,
                    )
                )
        }
    }
}

private fun RunManifest.toResponse() = RunManifestResponse(
    manifestId = manifestId.toString(),
    jobId = jobId.toString(),
    portfolioId = portfolioId,
    valuationDate = valuationDate.toString(),
    capturedAt = capturedAt.toString(),
    modelVersion = modelVersion,
    calculationType = calculationType,
    confidenceLevel = confidenceLevel,
    timeHorizonDays = timeHorizonDays,
    numSimulations = numSimulations,
    monteCarloSeed = monteCarloSeed,
    positionCount = positionCount,
    positionDigest = positionDigest,
    marketDataDigest = marketDataDigest,
    inputDigest = inputDigest,
    status = status.name,
    varValue = varValue,
    expectedShortfall = expectedShortfall,
    outputDigest = outputDigest,
)
