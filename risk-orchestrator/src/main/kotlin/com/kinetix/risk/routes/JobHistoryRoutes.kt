package com.kinetix.risk.routes

import com.kinetix.risk.mapper.toDetailResponse
import com.kinetix.risk.mapper.toSummaryResponse
import com.kinetix.risk.service.ValuationJobRecorder
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

fun Route.jobHistoryRoutes(jobRecorder: ValuationJobRecorder) {

    get("/api/v1/risk/jobs/{portfolioId}") {
        val portfolioId = call.requirePathParam("portfolioId")
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

        val from = try {
            call.request.queryParameters["from"]?.let { Instant.parse(it) }
        } catch (_: DateTimeParseException) {
            call.respond(HttpStatusCode.BadRequest, "Invalid 'from' timestamp")
            return@get
        }

        val to = try {
            call.request.queryParameters["to"]?.let { Instant.parse(it) }
        } catch (_: DateTimeParseException) {
            call.respond(HttpStatusCode.BadRequest, "Invalid 'to' timestamp")
            return@get
        }

        val jobs = jobRecorder.findByPortfolioId(portfolioId, limit, offset, from, to)
        call.respond(jobs.map { it.toSummaryResponse() })
    }

    get("/api/v1/risk/jobs/detail/{jobId}") {
        val jobIdStr = call.requirePathParam("jobId")
        val jobId = try {
            UUID.fromString(jobIdStr)
        } catch (_: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }
        val job = jobRecorder.findByJobId(jobId)
        if (job != null) {
            call.respond(job.toDetailResponse())
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}
