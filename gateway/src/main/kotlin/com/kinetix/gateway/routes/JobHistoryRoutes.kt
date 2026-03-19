package com.kinetix.gateway.routes

import com.kinetix.common.security.Permission
import com.kinetix.gateway.auth.requirePermission
import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.PaginatedJobsResponse
import com.kinetix.gateway.dto.toResponse
import com.kinetix.gateway.dto.ChartDataGatewayResponse
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.format.DateTimeParseException

fun Route.jobHistoryRoutes(client: RiskServiceClient) {

    get("/api/v1/risk/jobs/{bookId}/chart", {
        summary = "Get aggregated chart data"
        tags = listOf("Job History")
        request {
            pathParameter<String>("bookId") { description = "Book identifier" }
            queryParameter<String>("from") {
                description = "Start timestamp (ISO-8601)"
                required = true
            }
            queryParameter<String>("to") {
                description = "End timestamp (ISO-8601)"
                required = true
            }
        }
    }) {
        val bookId = call.requirePathParam("bookId")

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

        if (from == null || to == null) {
            call.respond(HttpStatusCode.BadRequest, "Both 'from' and 'to' are required")
            return@get
        }

        val data = client.getChartData(bookId, from, to)
        call.respond(data.toResponse())
    }

    get("/api/v1/risk/jobs/{bookId}", {
        summary = "List valuation jobs"
        tags = listOf("Job History")
        request {
            pathParameter<String>("bookId") { description = "Book identifier" }
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
            queryParameter<String>("valuationDate") {
                description = "Valuation date (YYYY-MM-DD). When set, filters to jobs for that business date."
                required = false
            }
        }
    }) {
        val bookId = call.requirePathParam("bookId")
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

        val valuationDate = call.request.queryParameters["valuationDate"]?.takeIf { it.isNotBlank() }

        val (jobs, totalCount) = client.listValuationJobs(bookId, limit, offset, from, to, valuationDate)
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

    requirePermission(Permission.PROMOTE_EOD_RUN) {
        patch("/api/v1/risk/jobs/{jobId}/label", {
            summary = "Promote or demote a job's EOD label"
            tags = listOf("EOD Promotion")
            request {
                pathParameter<String>("jobId") { description = "Job identifier" }
            }
        }) {
            val jobId = call.requirePathParam("jobId")
            val body = call.receive<JsonObject>()
            try {
                val result = client.promoteJobLabel(jobId, body)
                call.respond(result)
            } catch (e: com.kinetix.gateway.client.UpstreamErrorException) {
                call.respond(HttpStatusCode.fromValue(e.statusCode), mapOf("error" to e.message))
            }
        }
    }

    get("/api/v1/risk/jobs/{bookId}/official-eod", {
        summary = "Get the Official EOD designation for a book and date"
        tags = listOf("EOD Promotion")
        request {
            pathParameter<String>("bookId") { description = "Book identifier" }
            queryParameter<String>("date") {
                description = "Valuation date (YYYY-MM-DD)"
                required = true
            }
        }
    }) {
        val bookId = call.requirePathParam("bookId")
        val date = call.request.queryParameters["date"]
        if (date.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "date parameter is required"))
            return@get
        }
        val result = client.getOfficialEod(bookId, date)
        if (result != null) {
            call.respond(result)
        } else {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "No Official EOD designation"))
        }
    }
}
