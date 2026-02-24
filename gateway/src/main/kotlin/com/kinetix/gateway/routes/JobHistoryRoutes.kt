package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.toResponse
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.jobHistoryRoutes(client: RiskServiceClient) {

    get("/api/v1/risk/jobs/{portfolioId}") {
        val portfolioId = call.requirePathParam("portfolioId")
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
        val jobs = client.listValuationJobs(portfolioId, limit, offset)
        call.respond(jobs.map { it.toResponse() })
    }

    get("/api/v1/risk/jobs/detail/{jobId}") {
        val jobId = call.requirePathParam("jobId")
        val job = client.getValuationJobDetail(jobId)
        if (job != null) {
            call.respond(job.toResponse())
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}
