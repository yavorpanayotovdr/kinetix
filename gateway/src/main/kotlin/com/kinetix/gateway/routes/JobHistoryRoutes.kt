package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.PaginatedJobsResponse
import com.kinetix.gateway.dto.toResponse
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import java.time.format.DateTimeParseException

fun Route.jobHistoryRoutes(client: RiskServiceClient) {

    get("/api/v1/risk/jobs/{portfolioId}", {
        summary = "List valuation jobs"
        tags = listOf("Job History")
        request {
            pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
            queryParameter<Int>("limit") {
                description = "Max results, default 20"
                required = false
            }
            queryParameter<Int>("offset") {
                description = "Offset for pagination"
                required = false
            }
            queryParameter<String>("from") {
                description = "Start timestamp (ISO-8601)"
                required = false
            }
            queryParameter<String>("to") {
                description = "End timestamp (ISO-8601)"
                required = false
            }
        }
    }) {
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

        val (jobs, totalCount) = client.listValuationJobs(portfolioId, limit, offset, from, to)
        call.respond(PaginatedJobsResponse(jobs.map { it.toResponse() }, totalCount))
    }

    get("/api/v1/risk/jobs/detail/{jobId}", {
        summary = "Get valuation job details"
        tags = listOf("Job History")
        request {
            pathParameter<String>("jobId") { description = "Job identifier" }
        }
    }) {
        val jobId = call.requirePathParam("jobId")
        val job = client.getValuationJobDetail(jobId)
        if (job != null) {
            call.respond(job.toResponse())
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}
